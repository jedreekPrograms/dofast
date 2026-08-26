package com.doFast.dofastapp.job.cancellation.service;

import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.job.cancellation.dto.CreateJobCancellationRequest;
import com.doFast.dofastapp.job.cancellation.entity.JobCancellationRequest;
import com.doFast.dofastapp.job.cancellation.enums.JobCancellationStatus;
import com.doFast.dofastapp.job.cancellation.repository.JobCancellationRequestRepository;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.repository.JobRepository;
import com.doFast.dofastapp.location.tracking.service.LiveTrackingService;
import com.doFast.dofastapp.notification.service.NotificationService;
import com.doFast.dofastapp.payment.service.TransactionService;
import com.doFast.dofastapp.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobCancellationServiceTest {

    @Mock private JobRepository jobRepository;
    @Mock private JobCancellationRequestRepository cancellationRepository;
    @Mock private TransactionService transactionService;
    @Mock private LiveTrackingService liveTrackingService;
    @Mock private NotificationService notificationService;

    private JobCancellationService service;
    private User owner;
    private User worker;

    @BeforeEach
    void setUp() {
        service = new JobCancellationService(
                jobRepository,
                cancellationRepository,
                transactionService,
                liveTrackingService,
                notificationService
        );
        owner = user(1L, "owner@example.com");
        worker = user(2L, "worker@example.com");
    }

    @Test
    void cannotRequestNegotiatedCancellationBeforeJobIsAccepted() {
        Job job = job(JobStatus.OPEN, owner, null);
        when(jobRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(job));

        assertThrows(
                ConflictException.class,
                () -> service.requestCancellation(10L, new CreateJobCancellationRequest("Zmiana planów"), owner)
        );
        verify(cancellationRepository, never()).save(any());
    }

    @Test
    void requesterCannotApproveOwnCancellationRequest() {
        Job job = job(JobStatus.IN_PROGRESS, owner, worker);
        JobCancellationRequest request = JobCancellationRequest.pending(job, owner, "Zmiana planów", java.time.LocalDateTime.now());
        when(jobRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(job));
        when(cancellationRepository.findPendingForUpdate(10L, JobCancellationStatus.PENDING))
                .thenReturn(Optional.of(request));

        assertThrows(ForbiddenOperationException.class, () -> service.approve(10L, owner));
        verify(transactionService, never()).refundMoney(any());
        verify(liveTrackingService, never()).stopAndClear(any());
    }

    @Test
    void counterpartyApprovalCancelsFlushesTrackingAndRefundsEscrow() {
        Job job = job(JobStatus.IN_PROGRESS, owner, worker);
        JobCancellationRequest request = JobCancellationRequest.pending(job, owner, "Zmiana planów", java.time.LocalDateTime.now());
        ReflectionTestUtils.setField(request, "id", 44L);

        when(jobRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(job));
        when(cancellationRepository.findPendingForUpdate(10L, JobCancellationStatus.PENDING))
                .thenReturn(Optional.of(request));
        when(cancellationRepository.save(any(JobCancellationRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.approve(10L, worker);

        assertEquals(JobCancellationStatus.APPROVED, response.status());
        assertEquals(JobStatus.CANCELLED, job.getStatus());
        assertEquals(worker.getId(), response.resolvedById());

        InOrder order = inOrder(jobRepository, liveTrackingService, transactionService);
        order.verify(jobRepository).save(job);
        order.verify(jobRepository).flush();
        order.verify(liveTrackingService).stopAndClear(10L);
        order.verify(transactionService).refundMoney(job);
        verify(notificationService).notify(
                eq(owner),
                any(),
                any(),
                any(),
                eq(job),
                eq(null)
        );
    }

    private Job job(JobStatus status, User createdBy, User takenBy) {
        Job job = new Job();
        ReflectionTestUtils.setField(job, "id", 10L);
        job.setTitle("Test job");
        job.setDescription("Test job description");
        job.setPrice(new BigDecimal("20.00"));
        job.setStatus(status);
        job.setCreatedBy(createdBy);
        job.setTakenBy(takenBy);
        return job;
    }

    private User user(Long id, String email) {
        User user = new User(email, email);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
