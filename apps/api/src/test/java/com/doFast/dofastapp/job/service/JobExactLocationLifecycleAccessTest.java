package com.doFast.dofastapp.job.service;

import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.job.category.JobCategoryRepository;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.repository.JobRepository;
import com.doFast.dofastapp.job.search.alert.JobPublicationOutboxRepository;
import com.doFast.dofastapp.location.routing.service.RouteQuoteService;
import com.doFast.dofastapp.location.service.GeoPointFactory;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobExactLocationLifecycleAccessTest {

    @Mock private JobRepository jobRepository;
    @Mock private JobCategoryRepository jobCategoryRepository;
    @Mock private TransactionService transactionService;
    @Mock private NotificationService notificationService;
    @Mock private RouteQuoteService routeQuoteService;
    @Mock private LiveTrackingService liveTrackingService;
    @Mock private JobPublicationOutboxRepository jobPublicationOutboxRepository;

    private JobService jobService;
    private User requester;
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
        requester = user(1L, "requester@example.com");
        worker = user(2L, "worker@example.com");
    }

    @Test
    void requesterCanStillInspectExactLocationBeforeAssignment() {
        Job job = job(JobStatus.OPEN);
        when(jobRepository.findById(10L)).thenReturn(Optional.of(job));

        var location = jobService.getExactLocation(10L, requester);

        assertEquals(51.1128, location.latitude(), 0.000001);
        assertEquals(17.0601, location.longitude(), 0.000001);
        assertEquals("ul. Grunwaldzka 10, wejście A", location.label());
    }

    @Test
    void requesterCanInspectExactLocationWhileAcceptedJobIsActive() {
        Job job = job(JobStatus.DISPUTED);
        when(jobRepository.findById(10L)).thenReturn(Optional.of(job));

        var location = jobService.getExactLocation(10L, requester);

        assertEquals("ul. Grunwaldzka 10, wejście A", location.label());
    }

    @Test
    void requesterCannotReadExactLocationAfterCompletion() {
        Job job = job(JobStatus.DONE);
        when(jobRepository.findById(10L)).thenReturn(Optional.of(job));

        assertThrows(ForbiddenOperationException.class,
                () -> jobService.getExactLocation(10L, requester));
    }

    @Test
    void requesterCannotReadExactLocationAfterCancellation() {
        Job job = job(JobStatus.CANCELLED);
        when(jobRepository.findById(10L)).thenReturn(Optional.of(job));

        assertThrows(ForbiddenOperationException.class,
                () -> jobService.getExactLocation(10L, requester));
    }

    @Test
    void workerCannotReadExactLocationAfterCancellation() {
        Job job = job(JobStatus.CANCELLED);
        when(jobRepository.findById(10L)).thenReturn(Optional.of(job));

        assertThrows(ForbiddenOperationException.class,
                () -> jobService.getExactLocation(10L, worker));
    }

    private Job job(JobStatus status) {
        Job job = new Job();
        ReflectionTestUtils.setField(job, "id", 10L);
        job.setTitle("Test job");
        job.setDescription("Test description");
        job.setPrice(new BigDecimal("20.00"));
        job.setStatus(status);
        job.setLocation(GeoPointFactory.from(new BigDecimal("51.1128"), new BigDecimal("17.0601")));
        job.setLocationLabel("Wrocław, Plac Grunwaldzki");
        job.setLocationPrivateLabel("ul. Grunwaldzka 10, wejście A");
        job.setCreatedBy(requester);
        job.setTakenBy(worker);
        return job;
    }

    private User user(Long id, String email) {
        User user = new User(email, email);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
