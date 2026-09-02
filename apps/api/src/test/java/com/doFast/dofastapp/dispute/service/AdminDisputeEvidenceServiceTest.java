package com.doFast.dofastapp.dispute.service;

import com.doFast.dofastapp.chat.entity.ChatMessage;
import com.doFast.dofastapp.chat.repository.ChatMessageRepository;
import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.dispute.entity.Dispute;
import com.doFast.dofastapp.dispute.repository.DisputeRepository;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.enums.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDisputeEvidenceServiceTest {

    @Mock private DisputeRepository disputeRepository;
    @Mock private ChatMessageRepository chatMessageRepository;

    private AdminDisputeEvidenceService service;
    private User admin;
    private User requester;
    private Job job;
    private Dispute dispute;

    @BeforeEach
    void setUp() {
        service = new AdminDisputeEvidenceService(disputeRepository, chatMessageRepository);
        admin = user(10L, "admin", UserRole.ADMIN);
        requester = user(1L, "requester", UserRole.USER);
        User worker = user(2L, "worker", UserRole.USER);

        job = new Job();
        ReflectionTestUtils.setField(job, "id", 50L);
        job.setCreatedBy(requester);
        job.setTakenBy(worker);

        dispute = new Dispute();
        ReflectionTestUtils.setField(dispute, "id", 100L);
        dispute.setJob(job);
    }

    @Test
    void adminGetsOnlyMessagesFromDisputedJob() {
        when(disputeRepository.findById(100L)).thenReturn(Optional.of(dispute));
        ChatMessage message = new ChatMessage(job, requester, "Dowód z rozmowy", UUID.randomUUID());
        ReflectionTestUtils.setField(message, "id", 200L);
        when(chatMessageRepository.findByJobOrderByIdDesc(any(Job.class), any(Pageable.class)))
                .thenReturn(List.of(message));

        var response = service.getChatEvidence(100L, null, 100, admin);

        assertEquals(1, response.messages().size());
        assertEquals(50L, response.messages().getFirst().jobId());
        assertEquals("Dowód z rozmowy", response.messages().getFirst().content());
        verify(chatMessageRepository).findByJobOrderByIdDesc(any(Job.class), any(Pageable.class));
    }

    @Test
    void transientAdminCannotUseAdminEvidenceService() {
        User transientAdmin = new User("transient-admin@example.com", "transient-admin");
        transientAdmin.setRole(UserRole.ADMIN);

        assertThrows(
                ForbiddenOperationException.class,
                () -> service.getChatEvidence(100L, null, 100, transientAdmin)
        );

        verifyNoInteractions(disputeRepository, chatMessageRepository);
    }

    @Test
    void normalUserCannotUseAdminEvidenceService() {
        assertThrows(
                ForbiddenOperationException.class,
                () -> service.getChatEvidence(100L, null, 100, requester)
        );
        verify(disputeRepository, never()).findById(100L);
    }

    private User user(Long id, String nickname, UserRole role) {
        User user = new User(nickname + "@example.com", nickname);
        user.setRole(role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
