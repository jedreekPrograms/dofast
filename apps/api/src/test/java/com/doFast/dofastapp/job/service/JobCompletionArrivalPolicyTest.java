package com.doFast.dofastapp.job.service;

import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.job.category.FulfillmentMode;
import com.doFast.dofastapp.job.category.JobCategory;
import com.doFast.dofastapp.job.category.JobCategoryRepository;
import com.doFast.dofastapp.job.dto.JobResponse;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.repository.JobRepository;
import com.doFast.dofastapp.job.search.alert.JobPublicationOutboxRepository;
import com.doFast.dofastapp.location.routing.service.RouteQuoteService;
import com.doFast.dofastapp.location.tracking.dto.LiveTrackingResponse;
import com.doFast.dofastapp.location.tracking.enums.TrackingPhase;
import com.doFast.dofastapp.location.tracking.service.LiveTrackingService;
import com.doFast.dofastapp.notification.service.NotificationService;
import com.doFast.dofastapp.payment.service.TransactionService;
import com.doFast.dofastapp.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobCompletionArrivalPolicyTest {

    @Mock private JobRepository jobRepository;
    @Mock private JobCategoryRepository jobCategoryRepository;
    @Mock private TransactionService transactionService;
    @Mock private NotificationService notificationService;
    @Mock private RouteQuoteService routeQuoteService;
    @Mock private LiveTrackingService liveTrackingService;
    @Mock private JobPublicationOutboxRepository jobPublicationOutboxRepository;

    private JobService jobService;
    private User owner;
    private User worker;

    @BeforeEach
    void setUp() {
        jobService = new JobService(
                jobRepository,
                jobCategoryRepository,
                transactionService,
                notificationService,
                routeQuoteService,
                liveTrackingService,
                jobPublicationOutboxRepository
        );
        owner = user(1L, "owner@example.com");
        worker = user(2L, "worker@example.com");
    }

    @Test
    void pointToPointWorkerCannotRequestCompletionBeforeDestinationArrival() {
        Job job = activeJob(FulfillmentMode.POINT_TO_POINT);
        when(jobRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(job));
        when(liveTrackingService.getTracking(10L, worker)).thenReturn(tracking(TrackingPhase.TO_DESTINATION));

        assertThrows(ConflictException.class, () -> jobService.requestCompletion(10L, worker));

        assertEquals(JobStatus.IN_PROGRESS, job.getStatus());
        verify(jobRepository, never()).save(any(Job.class));
        verifyNoInteractions(notificationService);
    }

    @Test
    void pointToPointWorkerCanRequestCompletionAfterDestinationArrival() {
        Job job = activeJob(FulfillmentMode.POINT_TO_POINT);
        when(jobRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(job));
        when(liveTrackingService.getTracking(10L, worker)).thenReturn(tracking(TrackingPhase.ARRIVED_DESTINATION));
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JobResponse response = jobService.requestCompletion(10L, worker);

        assertEquals(JobStatus.COMPLETION_REQUESTED, response.status());
        verify(liveTrackingService).getTracking(10L, worker);
        verify(jobRepository).save(job);
    }

    @Test
    void onSiteWorkerDoesNotNeedDestinationTracking() {
        Job job = activeJob(FulfillmentMode.ON_SITE);
        when(jobRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(job));
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JobResponse response = jobService.requestCompletion(10L, worker);

        assertEquals(JobStatus.COMPLETION_REQUESTED, response.status());
        verify(liveTrackingService, never()).getTracking(any(), any());
    }

    private Job activeJob(FulfillmentMode fulfillmentMode) {
        JobCategory category = new JobCategory();
        ReflectionTestUtils.setField(category, "id", fulfillmentMode == FulfillmentMode.POINT_TO_POINT ? 42L : 43L);
        ReflectionTestUtils.setField(category, "slug", fulfillmentMode == FulfillmentMode.POINT_TO_POINT ? "mala-paczka" : "montaz-mebli");
        ReflectionTestUtils.setField(category, "name", fulfillmentMode == FulfillmentMode.POINT_TO_POINT ? "Mała paczka" : "Montaż mebli");
        ReflectionTestUtils.setField(category, "fulfillmentMode", fulfillmentMode);

        Job job = new Job();
        ReflectionTestUtils.setField(job, "id", 10L);
        job.setTitle("Test job");
        job.setDescription("Test job description");
        job.setPrice(new BigDecimal("20.00"));
        job.setStatus(JobStatus.IN_PROGRESS);
        job.setCategory(category);
        job.setCreatedBy(owner);
        job.setTakenBy(worker);
        return job;
    }

    private LiveTrackingResponse tracking(TrackingPhase phase) {
        return new LiveTrackingResponse(
                10L,
                worker.getId(),
                phase,
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                Instant.now(),
                false
        );
    }

    private User user(Long id, String email) {
        User user = new User(email, email);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
