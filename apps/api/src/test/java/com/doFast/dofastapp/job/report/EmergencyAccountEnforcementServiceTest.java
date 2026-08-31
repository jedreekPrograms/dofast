package com.doFast.dofastapp.job.report;

import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.dispute.entity.Dispute;
import com.doFast.dofastapp.dispute.entity.DisputeEvent;
import com.doFast.dofastapp.dispute.enums.DisputeReason;
import com.doFast.dofastapp.dispute.enums.DisputeStatus;
import com.doFast.dofastapp.dispute.repository.DisputeEventRepository;
import com.doFast.dofastapp.dispute.repository.DisputeRepository;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.repository.JobRepository;
import com.doFast.dofastapp.location.tracking.service.LiveTrackingService;
import com.doFast.dofastapp.notification.service.NotificationService;
import com.doFast.dofastapp.payment.service.TransactionService;
import com.doFast.dofastapp.user.auth.session.AuthSessionService;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.enums.UserRole;
import com.doFast.dofastapp.user.enums.UserStatus;
import com.doFast.dofastapp.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmergencyAccountEnforcementServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private JobRepository jobRepository;
    @Mock private DisputeRepository disputeRepository;
    @Mock private DisputeEventRepository disputeEventRepository;
    @Mock private TransactionService transactionService;
    @Mock private LiveTrackingService liveTrackingService;
    @Mock private AuthSessionService authSessionService;
    @Mock private NotificationService notificationService;

    private EmergencyAccountEnforcementService service;
    private User target;
    private User worker;
    private User admin;

    @BeforeEach
    void setUp() {
        service = new EmergencyAccountEnforcementService(
                userRepository,
                jobRepository,
                disputeRepository,
                disputeEventRepository,
                transactionService,
                liveTrackingService,
                authSessionService,
                notificationService
        );
        target = user(8L, "target@example.com", UserRole.USER);
        worker = user(10L, "worker@example.com", UserRole.USER);
        admin = user(9L, "admin@example.com", UserRole.ADMIN);
    }

    @Test
    void movesActiveJobToAdminSafetyDisputeBeforeSuspendingAccount() {
        Job active = job(11L, JobStatus.IN_PROGRESS, target, worker);
        Job open = job(12L, JobStatus.OPEN, target, null);
        when(userRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(target));
        when(jobRepository.findAllParticipantJobsWithStatusInForUpdate(any(), any())).thenReturn(List.of(active));
        when(disputeRepository.findFirstByJobAndStatusInOrderByOpenedAtDesc(any(), any()))
                .thenReturn(Optional.empty());
        when(disputeRepository.save(any(Dispute.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobRepository.findAllByStatusAndCreatedBy(JobStatus.OPEN, target)).thenReturn(List.of(open));
        when(userRepository.save(target)).thenReturn(target);

        User suspended = service.suspendJobOwner(8L, admin);

        assertEquals(UserStatus.SUSPENDED, suspended.getStatus());
        assertEquals(1L, suspended.getAuthVersion());
        assertEquals(JobStatus.DISPUTED, active.getStatus());
        assertEquals(JobStatus.CANCELLED, open.getStatus());
        verify(transactionService).assertHeld(active);
        verify(liveTrackingService).stopAndClear(11L);
        verify(authSessionService).revokeAllForUser(8L, "EMERGENCY_SUSPEND");

        ArgumentCaptor<Dispute> disputeCaptor = ArgumentCaptor.forClass(Dispute.class);
        verify(disputeRepository).save(disputeCaptor.capture());
        Dispute dispute = disputeCaptor.getValue();
        assertEquals(DisputeReason.SAFETY_CONCERN, dispute.getReason());
        assertEquals(DisputeStatus.UNDER_REVIEW, dispute.getStatus());
        assertEquals(JobStatus.IN_PROGRESS, dispute.getPreviousJobStatus());
        assertEquals(admin, dispute.getOpenedBy());
        assertEquals(admin, dispute.getAssignedAdmin());
        verify(disputeEventRepository, times(2)).save(any(DisputeEvent.class));
        verify(notificationService, times(2)).notify(any(), any(), any(), any(), any(), any());
    }

    @Test
    void preservesExistingDisputeAndOnlyStopsTracking() {
        Job disputedJob = job(21L, JobStatus.DISPUTED, target, worker);
        Dispute existing = new Dispute();
        existing.setJob(disputedJob);
        existing.setOpenedBy(worker);
        existing.setReason(DisputeReason.PAYMENT_ISSUE);
        existing.setDescription("existing");
        existing.setStatus(DisputeStatus.OPEN);
        existing.setPreviousJobStatus(JobStatus.IN_PROGRESS);
        when(userRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(target));
        when(jobRepository.findAllParticipantJobsWithStatusInForUpdate(any(), any())).thenReturn(List.of(disputedJob));
        when(disputeRepository.findFirstByJobAndStatusInOrderByOpenedAtDesc(any(), any()))
                .thenReturn(Optional.of(existing));
        when(jobRepository.findAllByStatusAndCreatedBy(JobStatus.OPEN, target)).thenReturn(List.of());
        when(userRepository.save(target)).thenReturn(target);

        service.suspendJobOwner(8L, admin);

        verify(transactionService).assertHeld(disputedJob);
        verify(disputeRepository, never()).save(any(Dispute.class));
        verify(disputeEventRepository, never()).save(any());
        verify(liveTrackingService).stopAndClear(21L);
        verify(authSessionService).revokeAllForUser(8L, "EMERGENCY_SUSPEND");
    }

    @Test
    void failsClosedWhenDisputedJobHasNoActiveDisputeRecord() {
        Job disputedJob = job(31L, JobStatus.DISPUTED, target, worker);
        when(userRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(target));
        when(jobRepository.findAllParticipantJobsWithStatusInForUpdate(any(), any())).thenReturn(List.of(disputedJob));
        when(disputeRepository.findFirstByJobAndStatusInOrderByOpenedAtDesc(any(), any()))
                .thenReturn(Optional.empty());

        assertThrows(ConflictException.class, () -> service.suspendJobOwner(8L, admin));

        assertEquals(UserStatus.ACTIVE, target.getStatus());
        assertEquals(0L, target.getAuthVersion());
        verify(liveTrackingService, never()).stopAndClear(any());
        verify(authSessionService, never()).revokeAllForUser(any(), any());
    }

    @Test
    void rejectsAdminTargetBeforeTouchingActiveWork() {
        target.setRole(UserRole.ADMIN);
        when(userRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(target));

        assertThrows(ConflictException.class, () -> service.suspendJobOwner(8L, admin));

        verify(jobRepository, never()).findAllParticipantJobsWithStatusInForUpdate(any(), any());
        verify(authSessionService, never()).revokeAllForUser(any(), any());
    }

    private User user(Long id, String email, UserRole role) {
        User user = new User(email, email.substring(0, email.indexOf('@')));
        ReflectionTestUtils.setField(user, "id", id);
        user.setRole(role);
        return user;
    }

    private Job job(Long id, JobStatus status, User owner, User contractor) {
        Job job = new Job();
        ReflectionTestUtils.setField(job, "id", id);
        job.setTitle("Safety test job " + id);
        job.setCreatedBy(owner);
        job.setTakenBy(contractor);
        job.setStatus(status);
        return job;
    }
}
