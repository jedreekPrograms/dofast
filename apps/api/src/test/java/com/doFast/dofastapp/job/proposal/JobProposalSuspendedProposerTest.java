package com.doFast.dofastapp.job.proposal;

import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.job.assignment.JobAssignmentMode;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.repository.JobRepository;
import com.doFast.dofastapp.location.tracking.service.LiveTrackingService;
import com.doFast.dofastapp.notification.service.NotificationService;
import com.doFast.dofastapp.payment.service.TransactionService;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.enums.UserStatus;
import com.doFast.dofastapp.user.service.UserBlockService;
import com.doFast.dofastapp.wallet.service.WalletService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobProposalSuspendedProposerTest {

    @Mock private JobRepository jobRepository;
    @Mock private JobProposalRepository jobProposalRepository;
    @Mock private TransactionService transactionService;
    @Mock private NotificationService notificationService;
    @Mock private LiveTrackingService liveTrackingService;
    @Mock private UserBlockService userBlockService;
    @Mock private WalletService walletService;
    @Mock private Job job;
    @Mock private JobProposal proposal;
    @Mock private User requester;
    @Mock private User suspendedWorker;

    @Test
    void fundingPreviewRejectsProposalFromSuspendedWorkerBeforeFinancialReads() {
        JobProposalService service = service();
        prepareOwnedOpenJob(false);
        prepareSuspendedProposal();

        assertThrows(
                ForbiddenOperationException.class,
                () -> service.getAcceptanceFunding(101L, 55L, requester)
        );

        verify(transactionService, never()).getHeldAmount(any(Job.class));
        verify(walletService, never()).getMyWallet(any());
        verify(userBlockService, never()).isInteractionBlocked(any(), any());
    }

    @Test
    void acceptanceRejectsProposalFromSuspendedWorkerBeforeEscrowOrAssignmentMutation() {
        JobProposalService service = service();
        prepareOwnedOpenJob(true);
        prepareSuspendedProposal();

        assertThrows(
                ForbiddenOperationException.class,
                () -> service.accept(101L, 55L, requester)
        );

        verify(transactionService, never()).adjustHeldAmount(any(), any(), any());
        verify(job, never()).setPrice(any());
        verify(job, never()).assignTo(any(), any());
        verify(jobRepository, never()).save(any(Job.class));
        verify(proposal, never()).accept(any());
        verify(userBlockService, never()).isInteractionBlocked(any(), any());
    }

    private JobProposalService service() {
        return new JobProposalService(
                jobRepository,
                jobProposalRepository,
                transactionService,
                notificationService,
                liveTrackingService,
                userBlockService,
                walletService
        );
    }

    private void prepareOwnedOpenJob(boolean forUpdate) {
        when(requester.getId()).thenReturn(11L);
        if (forUpdate) {
            when(jobRepository.findByIdAndCreatedByIdForUpdate(101L, 11L)).thenReturn(Optional.of(job));
        } else {
            when(jobRepository.findByIdAndCreatedBy_Id(101L, 11L)).thenReturn(Optional.of(job));
        }
        when(job.getAssignmentMode()).thenReturn(JobAssignmentMode.PROPOSALS);
        when(job.getCreatedBy()).thenReturn(requester);
        when(job.getStatus()).thenReturn(JobStatus.OPEN);
    }

    private void prepareSuspendedProposal() {
        when(jobProposalRepository.findByIdAndJob_Id(55L, 101L)).thenReturn(Optional.of(proposal));
        when(proposal.getStatus()).thenReturn(JobProposalStatus.SUBMITTED);
        when(proposal.getProposer()).thenReturn(suspendedWorker);
        when(suspendedWorker.getStatus()).thenReturn(UserStatus.SUSPENDED);
    }
}
