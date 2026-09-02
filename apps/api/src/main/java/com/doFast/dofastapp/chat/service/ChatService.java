package com.doFast.dofastapp.chat.service;

import com.doFast.dofastapp.chat.dto.ChatConversationResponse;
import com.doFast.dofastapp.chat.dto.ChatHistoryResponse;
import com.doFast.dofastapp.chat.dto.ChatMessageResponse;
import com.doFast.dofastapp.chat.entity.ChatMessage;
import com.doFast.dofastapp.chat.entity.ChatReadState;
import com.doFast.dofastapp.chat.repository.ChatMessageRepository;
import com.doFast.dofastapp.chat.repository.ChatReadStateRepository;
import com.doFast.dofastapp.common.exception.BusinessException;
import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.repository.JobRepository;
import com.doFast.dofastapp.notification.enums.NotificationType;
import com.doFast.dofastapp.notification.service.NotificationService;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.enums.UserStatus;
import com.doFast.dofastapp.user.repository.UserRepository;
import com.doFast.dofastapp.user.service.UserBlockService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatReadStateRepository chatReadStateRepository;
    private final JobRepository jobRepository;
    private final UserRepository userRepository;
    private final ChatAccessService chatAccessService;
    private final NotificationService notificationService;
    private final RealtimePublisher realtimePublisher;
    private final UserBlockService userBlockService;

    public ChatService(
            ChatMessageRepository chatMessageRepository,
            ChatReadStateRepository chatReadStateRepository,
            JobRepository jobRepository,
            UserRepository userRepository,
            ChatAccessService chatAccessService,
            NotificationService notificationService,
            RealtimePublisher realtimePublisher,
            UserBlockService userBlockService
    ) {
        this.chatMessageRepository = chatMessageRepository;
        this.chatReadStateRepository = chatReadStateRepository;
        this.jobRepository = jobRepository;
        this.userRepository = userRepository;
        this.chatAccessService = chatAccessService;
        this.notificationService = notificationService;
        this.realtimePublisher = realtimePublisher;
        this.userBlockService = userBlockService;
    }

    @Transactional
    public ChatMessageResponse sendMessage(
            Long jobId,
            User sender,
            String content,
            UUID clientMessageId
    ) {
        requireActorId(sender);
        String normalizedContent = normalizeContent(content);

        Optional<ChatMessage> duplicate = chatMessageRepository.findBySenderAndClientMessageId(
                sender,
                clientMessageId
        );
        if (duplicate.isPresent()) {
            ChatMessage existing = duplicate.get();
            if (!existing.getJob().getId().equals(jobId)) {
                throw new ConflictException("clientMessageId został już użyty dla innego zlecenia");
            }
            Job job = chatAccessService.requireParticipant(jobId, sender);
            User recipient = chatAccessService.otherParticipant(job, sender);
            requireInteractionAllowed(sender, recipient);
            return toResponse(existing);
        }

        Job job = chatAccessService.requireSendable(jobId, sender);
        User recipient = chatAccessService.otherParticipant(job, sender);
        requireInteractionAllowed(sender, recipient);

        ChatMessage saved = chatMessageRepository.save(
                new ChatMessage(job, sender, normalizedContent, clientMessageId)
        );
        ChatMessageResponse response = toResponse(saved);

        notificationService.notify(
                recipient,
                NotificationType.CHAT_MESSAGE,
                "Nowa wiadomość",
                sender.getNickname() + " napisał w zleceniu „" + job.getTitle() + "”",
                job,
                null
        );
        realtimePublisher.publishChat(job.getId(), response);

        return response;
    }

    @Transactional
    public ChatMessageResponse sendMessageByEmail(
            Long jobId,
            String senderEmail,
            String content,
            UUID clientMessageId
    ) {
        User sender = userRepository.findByEmailIgnoreCase(senderEmail)
                .orElseThrow(() -> new ForbiddenOperationException("Sesja czatu nie jest już ważna"));
        if (sender.getStatus() != UserStatus.ACTIVE) {
            throw new ForbiddenOperationException("Konto nie jest aktywne");
        }
        return sendMessage(jobId, sender, content, clientMessageId);
    }

    public ChatHistoryResponse getHistory(
            Long jobId,
            User user,
            Long beforeId,
            int limit
    ) {
        requireActorId(user);
        Job job = chatAccessService.requireParticipant(jobId, user);
        PageRequest page = PageRequest.of(0, limit + 1);
        List<ChatMessage> newestFirst = beforeId == null
                ? chatMessageRepository.findByJobOrderByIdDesc(job, page)
                : chatMessageRepository.findByJobAndIdLessThanOrderByIdDesc(job, beforeId, page);

        boolean hasMore = newestFirst.size() > limit;
        List<ChatMessage> visible = new ArrayList<>(
                newestFirst.subList(0, Math.min(limit, newestFirst.size()))
        );
        visible.sort(Comparator.comparing(ChatMessage::getId));

        List<ChatMessageResponse> messages = visible.stream()
                .map(this::toResponse)
                .toList();
        Long nextBeforeId = hasMore && !messages.isEmpty()
                ? messages.getFirst().id()
                : null;

        return new ChatHistoryResponse(messages, nextBeforeId, hasMore);
    }

    public List<ChatConversationResponse> getConversations(User user) {
        requireActorId(user);
        List<ConversationEntry> entries = jobRepository
                .findByCreatedByOrTakenByOrderByCreatedAtDesc(user, user)
                .stream()
                .filter(job -> job.getTakenBy() != null)
                .map(job -> toConversationEntry(job, user))
                .sorted(Comparator.comparing(
                        ConversationEntry::sortAt,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ).reversed())
                .toList();

        return entries.stream().map(ConversationEntry::response).toList();
    }

    @Transactional
    public void markRead(Long jobId, Long lastMessageId, User user) {
        requireActorId(user);
        Job job = chatAccessService.requireParticipant(jobId, user);
        ChatMessage message = chatMessageRepository.findByIdAndJob(lastMessageId, job)
                .orElseThrow(() -> new ResourceNotFoundException("Wiadomość nie istnieje w tym czacie"));

        ChatReadState state = chatReadStateRepository.findByJobAndUser(job, user)
                .orElseGet(() -> {
                    ChatReadState created = new ChatReadState();
                    created.setJob(job);
                    created.setUser(user);
                    created.setUpdatedAt(LocalDateTime.now());
                    return created;
                });

        state.advanceTo(message, LocalDateTime.now());
        chatReadStateRepository.save(state);
    }

    private ConversationEntry toConversationEntry(Job job, User user) {
        User other = chatAccessService.otherParticipant(job, user);
        Optional<ChatMessage> lastMessage = chatMessageRepository.findFirstByJobOrderByIdDesc(job);
        Long lastReadId = chatReadStateRepository.findByJobAndUser(job, user)
                .map(ChatReadState::getLastReadMessage)
                .map(ChatMessage::getId)
                .orElse(0L);
        long unread = chatMessageRepository.countByJobAndSenderNotAndIdGreaterThan(
                job,
                user,
                lastReadId
        );

        ChatMessageResponse lastResponse = lastMessage.map(this::toResponse).orElse(null);
        LocalDateTime sortAt = lastMessage.map(ChatMessage::getCreatedAt).orElse(job.getCreatedAt());

        return new ConversationEntry(
                new ChatConversationResponse(
                        job.getId(),
                        job.getTitle(),
                        job.getStatus(),
                        other.getId(),
                        other.getNickname(),
                        lastResponse,
                        unread
                ),
                sortAt
        );
    }

    private Long requireActorId(User user) {
        if (user == null || user.getId() == null) {
            throw new ForbiddenOperationException("Zaloguj się, aby korzystać z czatu");
        }
        return user.getId();
    }

    private void requireInteractionAllowed(User first, User second) {
        if (userBlockService.isInteractionBlocked(first, second)) {
            throw new ForbiddenOperationException("Wiadomość nie może zostać wysłana między zablokowanymi użytkownikami");
        }
    }

    private ChatMessageResponse toResponse(ChatMessage message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getJob().getId(),
                message.getSender().getId(),
                message.getSender().getNickname(),
                message.getContent(),
                message.getClientMessageId(),
                message.getCreatedAt()
        );
    }

    private String normalizeContent(String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException("Wiadomość nie może być pusta");
        }
        String normalized = content.trim();
        if (normalized.length() > 4000) {
            throw new BusinessException("Wiadomość może mieć maksymalnie 4000 znaków");
        }
        return normalized;
    }

    private record ConversationEntry(ChatConversationResponse response, LocalDateTime sortAt) {}
}
