package com.doFast.dofastapp.dispute.service;

import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.dispute.dto.CreateDisputeRequest;
import com.doFast.dofastapp.dispute.dto.ResolveDisputeRequest;
import com.doFast.dofastapp.dispute.entity.Dispute;
import com.doFast.dofastapp.dispute.enums.DisputeReason;
import com.doFast.dofastapp.dispute.enums.DisputeResolution;
import com.doFast.dofastapp.dispute.enums.DisputeStatus;
import com.doFast.dofastapp.dispute.repository.DisputeEventRepository;
import com.doFast.dofastapp.dispute.repository.DisputeRepository;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.repository.JobRepository;
import com.doFast.dofastapp.payment.service.TransactionService;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.enums.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DisputeServiceTest {

    @Mock private DisputeRepository disputeRepository;
    @Mock private DisputeEventRepository eventRepository;
    @Mock private JobRepository jobRepository;
    @Mock private TransactionService transactionService;

    private DisputeService disputeService;
    private User requester;
    private User worker;
    private User admin;

    @BeforeEach
    void setUp() {
        disputeService = new DisputeService(
                disputeRepository,
                eventRepository,
                jobRepository,
                transactionService
        );
        requester = user(1L, UserRole.USER, "requester");
        worker = user(2L, UserRole.USER, "worker");
        admin = user(10L, UserRole.ADMIN, "admin");
    }

    @Test
    void participantCanOpenDisputeAndEscrowRemainsHeld() {
        stubSuccessfulPersistence();
        Job job = job(JobStatus.IN_PROGRESS);
        when(jobRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(job));
        when(disputeRepository.findFirstByJobAndStatusInOrderByOpenedAtDesc(
                any(Job.class),
                any()
        )).thenReturn(Optional.empty());

        var response = disputeService.openDispute(
                new CreateDisputeRequest(50L, DisputeReason.NOT_COMPLETED, "Wykonawca nie wykonał zlecenia"),
                requester
        );

        assertEquals(DisputeStatus.OPEN, response.dispute().status());
        assertEquals(JobStatus.IN_PROGRESS, response.dispute().previousJobStatus());
        assertEquals(JobStatus.DISPUTED, job.getStatus());
        verify(transactionService).assertHeld(job);
        verify(transactionService, never()).refundMoney(job);
        verify(transactionService, never()).releaseMoney(any(), any());
    }

    @Test
    void unrelatedUserCannotOpenDispute() {
        Job job = job(JobStatus.IN_PROGRESS);
        when(jobRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(job));
        User stranger = user(3L, UserRole.USER, "stranger");

        assertThrows(
                ForbiddenOperationException.class,
                () -> disputeService.openDispute(
                        new CreateDisputeRequest(50L, DisputeReason.OTHER, "Nie moja sprawa"),
                        stranger
                )
        );
    }

    @Test
    void secondActiveDisputeIsRejected() {
        Job job = job(JobStatus.IN_PROGRESS);
        Dispute existing = dispute(job, requester, DisputeStatus.OPEN, JobStatus.IN_PROGRESS);
        when(jobRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(job));
        when(disputeRepository.findFirstByJobAndStatusInOrderByOpenedAtDesc(
                any(Job.class),
                any()
        )).thenReturn(Optional.of(existing));

        assertThrows(
                ConflictException.class,
                () -> disputeService.openDispute(
                        new CreateDisputeRequest(50L, DisputeReason.OTHER, "Drugi spór"),
                        requester
                )
        );
    }

    @Test
    void openerCanCancelBeforeAdminClaimsAndJobReturnsToPreviousStatus() {
        stubSuccessfulPersistence();
        Job job = job(JobStatus.DISPUTED);
        Dispute dispute = dispute(job, requester, DisputeStatus.OPEN, JobStatus.COMPLETION_REQUESTED);
        when(disputeRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(dispute));
        when(jobRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(job));

        var response = disputeService.cancelDispute(100L, requester);

        assertEquals(DisputeStatus.CANCELLED, response.dispute().status());
        assertEquals(JobStatus.COMPLETION_REQUESTED, job.getStatus());
        verify(transactionService).assertHeld(job);
    }

    @Test
    void adminCanReleaseHeldMoneyToWorker() {
        stubSuccessfulPersistence();
        Job job = job(JobStatus.DISPUTED);
        Dispute dispute = dispute(job, requester, DisputeStatus.UNDER_REVIEW, JobStatus.COMPLETION_REQUESTED);
        dispute.startReview(admin, LocalDateTime.now());
        when(disputeRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(dispute));
        when(jobRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(job));

        var response = disputeService.resolveDispute(
                100L,
                new ResolveDisputeRequest(DisputeResolution.RELEASE_TO_WORKER, "Dowody potwierdzają wykonanie"),
                admin
        );

        assertEquals(DisputeStatus.RESOLVED, response.dispute().status());
        assertEquals(DisputeResolution.RELEASE_TO_WORKER, response.dispute().resolution());
        assertEquals(JobStatus.DONE, job.getStatus());
        verify(transactionService).releaseMoney(job, worker);
    }

    @Test
    void adminCanRefundRequester() {
        stubSuccessfulPersistence();
        Job job = job(JobStatus.DISPUTED);
        Dispute dispute = dispute(job, worker, DisputeStatus.OPEN, JobStatus.IN_PROGRESS);
        when(disputeRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(dispute));
        when(jobRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(job));

        disputeService.resolveDispute(
                100L,
                new ResolveDisputeRequest(DisputeResolution.REFUND_TO_REQUESTER, "Zlecenie nie zostało wykonane"),
                admin
        );

        assertEquals(JobStatus.CANCELLED, job.getStatus());
        verify(transactionService).refundMoney(job);
    }

    @Test
    void adminCanResumeJobWithoutMovingEscrow() {
        stubSuccessfulPersistence();
        Job job = job(JobStatus.DISPUTED);
        Dispute dispute = dispute(job, requester, DisputeStatus.OPEN, JobStatus.IN_PROGRESS);
        when(disputeRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(dispute));
        when(jobRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(job));

        disputeService.resolveDispute(
                100L,
                new ResolveDisputeRequest(DisputeResolution.RESUME_JOB, "Strony mogą kontynuować"),
                admin
        );

        assertEquals(JobStatus.IN_PROGRESS, job.getStatus());
        verify(transactionService).assertHeld(job);
        verify(transactionService, never()).refundMoney(job);
        verify(transactionService, never()).releaseMoney(any(), any());
    }

    @Test
    void anotherAdminCannotResolveClaimedCase() {
        Job job = job(JobStatus.DISPUTED);
        Dispute dispute = dispute(job, requester, DisputeStatus.UNDER_REVIEW, JobStatus.IN_PROGRESS);
        dispute.startReview(admin, LocalDateTime.now());
        when(disputeRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(dispute));
        User otherAdmin = user(11L, UserRole.ADMIN, "other-admin");

        assertThrows(
                ConflictException.class,
                () -> disputeService.resolveDispute(
                        100L,
                        new ResolveDisputeRequest(DisputeResolution.RESUME_JOB, "Próba przejęcia"),
                        otherAdmin
                )
        );
    }

    private void stubSuccessfulPersistence() {
        when(eventRepository.findByDispute_IdOrderByCreatedAtAsc(anyLong())).thenReturn(List.of());
        when(disputeRepository.save(any(Dispute.class))).thenAnswer(invocation -> {
            Dispute dispute = invocation.getArgument(0);
            if (dispute.getId() == null) {
                ReflectionTestUtils.setField(dispute, "id", 100L);
            }
            return dispute;
        });
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Job job(JobStatus status) {
        Job job = new Job();
        ReflectionTestUtils.setField(job, "id", 50L);
        job.setTitle("Testowe zlecenie");
        job.setDescription("Opis zlecenia");
        job.setPrice(new BigDecimal("25.00"));
        job.setCreatedBy(requester);
        job.setTakenBy(worker);
        job.setStatus(status);
        return job;
    }

    private Dispute dispute(Job job, User openedBy, DisputeStatus status, JobStatus previousStatus) {
        Dispute dispute = new Dispute();
        ReflectionTestUtils.setField(dispute, "id", 100L);
        dispute.setJob(job);
        dispute.setOpenedBy(openedBy);
        dispute.setReason(DisputeReason.OTHER);
        dispute.setDescription("Opis sporu");
        dispute.setStatus(status);
        dispute.setPreviousJobStatus(previousStatus);
        dispute.setOpenedAt(LocalDateTime.now());
        return dispute;
    }

    private User user(Long id, UserRole role, String nickname) {
        User user = new User(nickname + "@example.com", nickname);
        user.setRole(role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
