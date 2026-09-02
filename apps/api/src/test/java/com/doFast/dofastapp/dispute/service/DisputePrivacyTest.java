package com.doFast.dofastapp.dispute.service;

import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
import com.doFast.dofastapp.dispute.entity.Dispute;
import com.doFast.dofastapp.dispute.enums.DisputeReason;
import com.doFast.dofastapp.dispute.enums.DisputeStatus;
import com.doFast.dofastapp.dispute.repository.DisputeEventRepository;
import com.doFast.dofastapp.dispute.repository.DisputeRepository;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.expense.JobExpenseService;
import com.doFast.dofastapp.job.repository.JobRepository;
import com.doFast.dofastapp.notification.service.NotificationService;
import com.doFast.dofastapp.payment.service.TransactionService;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.enums.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DisputePrivacyTest {

    @Mock private DisputeRepository disputeRepository;
    @Mock private DisputeEventRepository eventRepository;
    @Mock private JobRepository jobRepository;
    @Mock private TransactionService transactionService;
    @Mock private NotificationService notificationService;
    @Mock private JobExpenseService expenseService;

    private DisputeService service;
    private User owner;
    private User worker;
    private User outsider;
    private User admin;
    private Dispute dispute;

    @BeforeEach
    void setUp() {
        service = new DisputeService(disputeRepository, eventRepository, jobRepository,
                transactionService, notificationService, expenseService);
        owner = user(1L, UserRole.USER, "owner");
        worker = user(2L, UserRole.USER, "worker");
        outsider = user(3L, UserRole.USER, "outsider");
        admin = user(10L, UserRole.ADMIN, "admin");

        Job job = new Job();
        ReflectionTestUtils.setField(job, "id", 50L);
        job.setTitle("Sensitive job");
        job.setCreatedBy(owner);
        job.setTakenBy(worker);
        job.setStatus(JobStatus.DISPUTED);

        dispute = new Dispute();
        ReflectionTestUtils.setField(dispute, "id", 100L);
        dispute.setJob(job);
        dispute.setOpenedBy(owner);
        dispute.setReason(DisputeReason.OTHER);
        dispute.setDescription("Sensitive dispute details");
        dispute.setStatus(DisputeStatus.OPEN);
        dispute.setPreviousJobStatus(JobStatus.IN_PROGRESS);
        dispute.setOpenedAt(LocalDateTime.now());
    }

    @Test
    void outsiderGetsNeutralNotFoundWithoutGlobalDisputeLoad() {
        when(disputeRepository.findParticipantById(100L, outsider.getId())).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getDispute(100L, outsider));

        verify(disputeRepository, never()).findById(100L);
        verify(eventRepository, never()).findByDispute_IdOrderByCreatedAtAsc(anyLong());
    }

    @Test
    void participantStillReadsOwnDisputeThroughScopedLookup() {
        when(disputeRepository.findParticipantById(100L, owner.getId())).thenReturn(Optional.of(dispute));
        when(disputeRepository.findParticipantById(100L, worker.getId())).thenReturn(Optional.of(dispute));
        when(eventRepository.findByDispute_IdOrderByCreatedAtAsc(100L)).thenReturn(List.of());

        assertEquals(100L, service.getDispute(100L, owner).dispute().id());
        assertEquals(100L, service.getDispute(100L, worker).dispute().id());

        verify(disputeRepository, never()).findById(100L);
    }

    @Test
    void missingIdentityFailsClosedBeforeAnyDisputeLookup() {
        User transientUser = new User("transient@example.com", "transient");

        assertThrows(ResourceNotFoundException.class, () -> service.getDispute(100L, transientUser));

        verify(disputeRepository, never()).findParticipantById(anyLong(), anyLong());
        verify(disputeRepository, never()).findById(anyLong());
        verify(eventRepository, never()).findByDispute_IdOrderByCreatedAtAsc(anyLong());
    }

    @Test
    void transientAdminCannotUseGlobalReadOrPrivateListPath() {
        User transientAdmin = new User("transient-admin@example.com", "transient-admin");
        transientAdmin.setRole(UserRole.ADMIN);

        assertThrows(ResourceNotFoundException.class, () -> service.getDispute(100L, transientAdmin));
        assertThrows(ResourceNotFoundException.class, () -> service.getMyDisputes(transientAdmin));

        verify(disputeRepository, never()).findById(anyLong());
        verify(disputeRepository, never()).findParticipantById(anyLong(), anyLong());
        verify(disputeRepository, never()).findAllForParticipant(transientAdmin);
        verify(eventRepository, never()).findByDispute_IdOrderByCreatedAtAsc(anyLong());
    }

    @Test
    void adminStillReadsDisputeThroughAdministrativeGlobalPath() {
        when(disputeRepository.findById(100L)).thenReturn(Optional.of(dispute));
        when(eventRepository.findByDispute_IdOrderByCreatedAtAsc(100L)).thenReturn(List.of());

        assertEquals(100L, service.getDispute(100L, admin).dispute().id());

        verify(disputeRepository, never()).findParticipantById(anyLong(), anyLong());
    }

    private User user(Long id, UserRole role, String nickname) {
        User user = new User(nickname + "@example.com", nickname);
        user.setRole(role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
