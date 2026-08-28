package com.doFast.dofastapp.chat.service;

import com.doFast.dofastapp.chat.dto.ChatHistoryResponse;
import com.doFast.dofastapp.chat.entity.ChatMessage;
import com.doFast.dofastapp.chat.entity.ChatReadState;
import com.doFast.dofastapp.chat.repository.ChatMessageRepository;
import com.doFast.dofastapp.chat.repository.ChatReadStateRepository;
import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.repository.JobRepository;
import com.doFast.dofastapp.notification.enums.NotificationType;
import com.doFast.dofastapp.notification.service.NotificationService;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.repository.UserRepository;
import com.doFast.dofastapp.user.service.UserBlockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private ChatReadStateRepository chatReadStateRepository;
    @Mock private JobRepository jobRepository;
    @Mock private UserRepository userRepository;
    @Mock private ChatAccessService chatAccessService;
    @Mock private NotificationService notificationService;
    @Mock private RealtimePublisher realtimePublisher;
    @Mock private UserBlockService userBlockService;

    private ChatService chatService;
    private User owner;
    private User worker;
    private Job job;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(
                chatMessageRepository,
                chatReadStateRepository,
                jobRepository,
                userRepository,
                chatAccessService,
                notificationService,
                realtimePublisher,
                userBlockService
        );
        owner = user(1L, "owner", "owner@example.com");
        worker = user(2L, "worker", "worker@example.com");
        job = job(10L, JobStatus.IN_PROGRESS, owner, worker);
    }

    @Test
    void sendMessageUsesAuthenticatedUserPersistsNotifiesAndPublishes() {
        UUID clientMessageId = UUID.randomUUID();
        when(chatMessageRepository.findBySenderAndClientMessageId(owner, clientMessageId))
                .thenReturn(Optional.empty());
        when(chatAccessService.requireSendable(10L, owner)).thenReturn(job);
        when(chatAccessService.otherParticipant(job, owner)).thenReturn(worker);
        when(userBlockService.isInteractionBlocked(owner, worker)).thenReturn(false);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage message = invocation.getArgument(0);
            ReflectionTestUtils.setField(message, "id", 100L);
            return message;
        });

        var response = chatService.sendMessage(10L, owner, "  Cześć!  ", clientMessageId);

        assertEquals(100L, response.id());
        assertEquals(owner.getId(), response.senderId());
        assertEquals("Cześć!", response.content());
        assertEquals(clientMessageId, response.clientMessageId());
        verify(notificationService).notify(
                eq(worker),
                eq(NotificationType.CHAT_MESSAGE),
                eq("Nowa wiadomość"),
                any(String.class),
                eq(job),
                eq(null)
        );
        verify(realtimePublisher).publishChat(10L, response);
    }

    @Test
    void blockedParticipantsCannotSendMessageOrTriggerSideEffects() {
        UUID clientMessageId = UUID.randomUUID();
        when(chatMessageRepository.findBySenderAndClientMessageId(owner, clientMessageId))
                .thenReturn(Optional.empty());
        when(chatAccessService.requireSendable(10L, owner)).thenReturn(job);
        when(chatAccessService.otherParticipant(job, owner)).thenReturn(worker);
        when(userBlockService.isInteractionBlocked(owner, worker)).thenReturn(true);

        assertThrows(
                ForbiddenOperationException.class,
                () -> chatService.sendMessage(10L, owner, "Nie powinno przejść", clientMessageId)
        );

        verify(chatMessageRepository, never()).save(any());
        verify(notificationService, never()).notify(any(), any(), any(), any(), any(), any());
        verify(realtimePublisher, never()).publishChat(any(), any());
    }

    @Test
    void duplicateClientMessageIdReturnsExistingMessageWithoutSideEffects() {
        UUID clientMessageId = UUID.randomUUID();
        ChatMessage existing = message(55L, job, owner, "Ta sama wiadomość", clientMessageId);
        when(chatMessageRepository.findBySenderAndClientMessageId(owner, clientMessageId))
                .thenReturn(Optional.of(existing));
        when(chatAccessService.requireParticipant(10L, owner)).thenReturn(job);
        when(chatAccessService.otherParticipant(job, owner)).thenReturn(worker);
        when(userBlockService.isInteractionBlocked(owner, worker)).thenReturn(false);

        var response = chatService.sendMessage(10L, owner, "Ta sama wiadomość", clientMessageId);

        assertEquals(55L, response.id());
        verify(chatMessageRepository, never()).save(any());
        verify(notificationService, never()).notify(any(), any(), any(), any(), any(), any());
        verify(realtimePublisher, never()).publishChat(any(), any());
    }

    @Test
    void reusedClientMessageIdForAnotherJobIsRejected() {
        UUID clientMessageId = UUID.randomUUID();
        Job otherJob = job(11L, JobStatus.IN_PROGRESS, owner, worker);
        ChatMessage existing = message(55L, otherJob, owner, "Inny czat", clientMessageId);
        when(chatMessageRepository.findBySenderAndClientMessageId(owner, clientMessageId))
                .thenReturn(Optional.of(existing));

        assertThrows(
                ConflictException.class,
                () -> chatService.sendMessage(10L, owner, "Nowa próba", clientMessageId)
        );
    }

    @Test
    void historyUsesKeysetAndReturnsChronologicalMessages() {
        when(chatAccessService.requireParticipant(10L, owner)).thenReturn(job);
        ChatMessage newest = message(30L, job, worker, "trzecia", UUID.randomUUID());
        ChatMessage middle = message(20L, job, owner, "druga", UUID.randomUUID());
        ChatMessage oldestExtra = message(10L, job, worker, "pierwsza", UUID.randomUUID());
        when(chatMessageRepository.findByJobOrderByIdDesc(eq(job), any(Pageable.class)))
                .thenReturn(List.of(newest, middle, oldestExtra));

        ChatHistoryResponse response = chatService.getHistory(10L, owner, null, 2);

        assertTrue(response.hasMore());
        assertEquals(20L, response.nextBeforeId());
        assertEquals(List.of(20L, 30L), response.messages().stream().map(message -> message.id()).toList());
    }

    @Test
    void markReadNeverMovesCursorBackwards() {
        when(chatAccessService.requireParticipant(10L, owner)).thenReturn(job);
        ChatMessage alreadyRead = message(50L, job, worker, "nowsza", UUID.randomUUID());
        ChatMessage requestedOlder = message(40L, job, worker, "starsza", UUID.randomUUID());
        ChatReadState state = new ChatReadState();
        state.setJob(job);
        state.setUser(owner);
        state.advanceTo(alreadyRead, java.time.LocalDateTime.now());
        when(chatMessageRepository.findByIdAndJob(40L, job)).thenReturn(Optional.of(requestedOlder));
        when(chatReadStateRepository.findByJobAndUser(job, owner)).thenReturn(Optional.of(state));

        chatService.markRead(10L, 40L, owner);

        assertEquals(50L, state.getLastReadMessage().getId());
        verify(chatReadStateRepository).save(state);
    }

    @Test
    void conversationSummaryIncludesUnreadFromOtherParticipantOnly() {
        when(jobRepository.findByCreatedByOrTakenByOrderByCreatedAtDesc(owner, owner)).thenReturn(List.of(job));
        when(chatAccessService.otherParticipant(job, owner)).thenReturn(worker);
        ChatMessage last = message(80L, job, worker, "Masz chwilę?", UUID.randomUUID());
        when(chatMessageRepository.findFirstByJobOrderByIdDesc(job)).thenReturn(Optional.of(last));
        when(chatReadStateRepository.findByJobAndUser(job, owner)).thenReturn(Optional.empty());
        when(chatMessageRepository.countByJobAndSenderNotAndIdGreaterThan(job, owner, 0L)).thenReturn(3L);

        var conversations = chatService.getConversations(owner);

        assertEquals(1, conversations.size());
        assertEquals(3L, conversations.getFirst().unreadCount());
        assertEquals("worker", conversations.getFirst().otherUserNickname());
        assertFalse(conversations.getFirst().lastMessage().content().isBlank());
    }

    private User user(Long id, String nickname, String email) {
        User user = new User(email, nickname);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Job job(Long id, JobStatus status, User createdBy, User takenBy) {
        Job job = new Job();
        ReflectionTestUtils.setField(job, "id", id);
        job.setTitle("Test chat job");
        job.setDescription("Opis");
        job.setPrice(new BigDecimal("20.00"));
        job.setStatus(status);
        job.setCreatedBy(createdBy);
        job.setTakenBy(takenBy);
        ReflectionTestUtils.setField(job, "createdAt", java.time.LocalDateTime.now());
        return job;
    }

    private ChatMessage message(
            Long id,
            Job messageJob,
            User sender,
            String content,
            UUID clientMessageId
    ) {
        ChatMessage message = new ChatMessage(messageJob, sender, content, clientMessageId);
        ReflectionTestUtils.setField(message, "id", id);
        return message;
    }
}
