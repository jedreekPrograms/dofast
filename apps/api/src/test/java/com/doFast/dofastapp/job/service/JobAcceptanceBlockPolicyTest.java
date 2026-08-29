package com.doFast.dofastapp.job.service;

import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.job.category.JobCategoryRepository;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.expense.JobExpenseService;
import com.doFast.dofastapp.job.repository.JobRepository;
import com.doFast.dofastapp.job.search.alert.JobPublicationOutboxRepository;
import com.doFast.dofastapp.location.routing.service.RouteQuoteService;
import com.doFast.dofastapp.location.tracking.service.LiveTrackingService;
import com.doFast.dofastapp.notification.service.NotificationService;
import com.doFast.dofastapp.payment.service.TransactionService;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.service.UserBlockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobAcceptanceBlockPolicyTest {

    @Mock private JobRepository jobRepository;
    @Mock private JobCategoryRepository jobCategoryRepository;
    @Mock private TransactionService transactionService;
    @Mock private NotificationService notificationService;
    @Mock private RouteQuoteService routeQuoteService;
    @Mock private LiveTrackingService liveTrackingService;
    @Mock private JobPublicationOutboxRepository jobPublicationOutboxRepository;
    @Mock private UserBlockService userBlockService;
    @Mock private JobExpenseService expenseService;

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
                jobPublicationOutboxRepository,
                userBlockService,
                expenseService
        );
        owner = user(1L, "owner@example.com");
        worker = user(2L, "worker@example.com");
    }

    @Test
    void blockedRelationshipCannotAcceptOpenJobOrTriggerSideEffects() {
        Job job = new Job();
        ReflectionTestUtils.setField(job, "id", 10L);
        job.setTitle("Test job");
        job.setDescription("Test job description");
        job.setPrice(new BigDecimal("20.00"));
        job.setStatus(JobStatus.OPEN);
        job.setCreatedBy(owner);

        when(jobRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(job));
        when(userBlockService.isInteractionBlocked(owner, worker)).thenReturn(true);

        assertThrows(ForbiddenOperationException.class, () -> jobService.acceptJob(10L, worker));

        verify(jobRepository, never()).save(job);
        verify(liveTrackingService, never()).initializeForAcceptedJob(job);
        verify(notificationService, never()).notify(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    private User user(Long id, String email) {
        User user = new User(email, email);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
