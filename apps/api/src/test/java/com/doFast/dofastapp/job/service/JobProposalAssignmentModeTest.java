package com.doFast.dofastapp.job.service;

import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.job.assignment.JobAssignmentMode;
import com.doFast.dofastapp.job.category.JobCategoryRepository;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.repository.JobRepository;
import com.doFast.dofastapp.job.search.alert.JobPublicationOutboxRepository;
import com.doFast.dofastapp.location.routing.service.RouteQuoteService;
import com.doFast.dofastapp.location.tracking.service.LiveTrackingService;
import com.doFast.dofastapp.notification.service.NotificationService;
import com.doFast.dofastapp.payment.service.TransactionService;
import com.doFast.dofastapp.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobProposalAssignmentModeTest {

    @Mock private JobRepository jobRepository;
    @Mock private JobCategoryRepository jobCategoryRepository;
    @Mock private TransactionService transactionService;
    @Mock private NotificationService notificationService;
    @Mock private RouteQuoteService routeQuoteService;
    @Mock private LiveTrackingService liveTrackingService;
    @Mock private JobPublicationOutboxRepository jobPublicationOutboxRepository;
    @Mock private Job job;
    @Mock private User worker;

    @Test
    void proposalBasedJobCannotBypassSelectionThroughDirectAccept() {
        when(worker.getId()).thenReturn(2L);
        when(jobRepository.findByIdAndStatusAndAssignmentModeForUpdate(
                91L, JobStatus.OPEN, JobAssignmentMode.INSTANT
        )).thenReturn(Optional.empty());
        when(jobRepository.findByIdAndStatus(91L, JobStatus.OPEN)).thenReturn(Optional.of(job));
        when(job.getAssignmentMode()).thenReturn(JobAssignmentMode.PROPOSALS);

        JobService service = new JobService(
                jobRepository,
                jobCategoryRepository,
                transactionService,
                notificationService,
                routeQuoteService,
                liveTrackingService,
                jobPublicationOutboxRepository
        );

        assertThrows(ConflictException.class, () -> service.acceptJob(91L, worker));

        verify(jobRepository, never()).findByIdForUpdate(91L);
        verify(job, never()).assignTo(eq(worker), any());
        verify(jobRepository, never()).save(job);
        verify(liveTrackingService, never()).initializeForAcceptedJob(job);
    }
}
