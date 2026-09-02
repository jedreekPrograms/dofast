package com.doFast.dofastapp.dispute.service;

import com.doFast.dofastapp.chat.dto.ChatHistoryResponse;
import com.doFast.dofastapp.chat.dto.ChatMessageResponse;
import com.doFast.dofastapp.chat.entity.ChatMessage;
import com.doFast.dofastapp.chat.repository.ChatMessageRepository;
import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
import com.doFast.dofastapp.dispute.entity.Dispute;
import com.doFast.dofastapp.dispute.repository.DisputeRepository;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.enums.UserRole;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class AdminDisputeEvidenceService {

    private final DisputeRepository disputeRepository;
    private final ChatMessageRepository chatMessageRepository;

    public AdminDisputeEvidenceService(
            DisputeRepository disputeRepository,
            ChatMessageRepository chatMessageRepository
    ) {
        this.disputeRepository = disputeRepository;
        this.chatMessageRepository = chatMessageRepository;
    }

    public ChatHistoryResponse getChatEvidence(
            Long disputeId,
            Long beforeId,
            int limit,
            User admin
    ) {
        if (admin == null || admin.getId() == null || admin.getRole() != UserRole.ADMIN) {
            throw new ForbiddenOperationException("Ta operacja wymaga uprawnień administratora");
        }

        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException("Spór nie istnieje"));

        PageRequest page = PageRequest.of(0, limit + 1);
        List<ChatMessage> newestFirst = beforeId == null
                ? chatMessageRepository.findByJobOrderByIdDesc(dispute.getJob(), page)
                : chatMessageRepository.findByJobAndIdLessThanOrderByIdDesc(
                        dispute.getJob(),
                        beforeId,
                        page
                );

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
}
