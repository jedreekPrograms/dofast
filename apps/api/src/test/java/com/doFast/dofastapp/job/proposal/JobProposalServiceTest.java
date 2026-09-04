package com.doFast.dofastapp.job.proposal;

import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.common.exception.BusinessException;
import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
import com.doFast.dofastapp.job.assignment.JobAssignmentMode;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.repository.JobRepository;
import com.doFast.dofastapp.location.tracking.service.LiveTrackingService;
import com.doFast.dofastapp.notification.enums.NotificationType;
import com.doFast.dofastapp.notification.service.NotificationService;
import com.doFast.dofastapp.payment.service.TransactionService;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.service.UserBlockService;
import com.doFast.dofastapp.wallet.dto.WalletResponse;
import com.doFast.dofastapp.wallet.service.WalletService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobProposalServiceTest {

    @Mock private JobRepository jobRepository;
    @Mock private JobProposalRepository jobProposalRepository;
    @Mock private TransactionService transactionService;
    @Mock private NotificationService notificationService;
    @Mock private LiveTrackingService liveTrackingService;
    @Mock private UserBlockService userBlockService;
    @Mock private WalletService walletService;
    @Mock private Job job;
    @Mock private User requester;
    @Mock private User worker;
    @Mock private JobProposal proposal;

    @Test
    void fixedPriceProposalDefaultsToPublishedPrice() {
        JobProposalService service = service();
        prepareOpenProposalJobForSubmit();
        when(worker.getId()).thenReturn(22L);
        when(worker.getNickname()).thenReturn("Rowerzysta");
        when(jobProposalRepository.findByJob_IdAndProposer_Id(101L, 22L)).thenReturn(Optional.empty());
        when(jobProposalRepository.save(any(JobProposal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JobProposalResponse response = service.submit(
                101L,
                new CreateJobProposalRequest(null, "Mogę zrobić od razu"),
                worker
        );

        assertEquals(new BigDecimal("30.00"), response.amount());
        assertEquals(JobProposalStatus.SUBMITTED, response.status());
        verify(notificationService).notify(
                requester,
                NotificationType.JOB_PROPOSAL_RECEIVED,
                "Nowa propozycja do zlecenia",
                "Rowerzysta wysłał propozycję do „Zakupy” za 30.00 zł.",
                job,
                null
        );
    }

    @Test
    void fixedPriceProposalRejectsDifferentAmountWhenNegotiationIsDisabled() {
        JobProposalService service = service();
        prepareOpenProposalJobForSubmit();
        when(worker.getId()).thenReturn(22L);
        when(jobProposalRepository.findByJob_IdAndProposer_Id(101L, 22L)).thenReturn(Optional.empty());

        assertThrows(
                BusinessException.class,
                () -> service.submit(
                        101L,
                        new CreateJobProposalRequest(new BigDecimal("35.00"), null),
                        worker
                )
        );

        verify(jobProposalRepository, never()).save(any(JobProposal.class));
        verify(notificationService, never()).notify(any(), any(), anyString(), anyString(), any(), any());
    }

    @Test
    void blockedRelationshipCannotSubmitProposalOrTriggerSideEffects() {
        JobProposalService service = service();
        when(worker.getId()).thenReturn(22L);
        when(requester.getId()).thenReturn(11L);
        when(jobRepository.findByIdAndStatusAndAssignmentModeForUpdate(
                101L,
                JobStatus.OPEN,
                JobAssignmentMode.PROPOSALS
        )).thenReturn(Optional.of(job));
        when(job.getCreatedBy()).thenReturn(requester);
        when(userBlockService.isInteractionBlocked(requester, worker)).thenReturn(true);

        assertThrows(
                ForbiddenOperationException.class,
                () -> service.submit(101L, new CreateJobProposalRequest(null, null), worker)
        );

        verify(jobProposalRepository, never()).findByJob_IdAndProposer_Id(101L, 22L);
        verify(jobProposalRepository, never()).save(any(JobProposal.class));
        verifyNoInteractions(transactionService, walletService, notificationService, liveTrackingService);
    }

    @Test
    void submitHidesUnavailableOrInstantJobBehindOpenProposalLookup() {
        JobProposalService service = service();
        when(worker.getId()).thenReturn(22L);
        when(jobRepository.findByIdAndStatusAndAssignmentModeForUpdate(
                101L,
                JobStatus.OPEN,
                JobAssignmentMode.PROPOSALS
        )).thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> service.submit(101L, new CreateJobProposalRequest(null, null), worker)
        );

        verify(jobRepository, never()).findByIdForUpdate(101L);
        verify(jobProposalRepository, never()).findByJob_IdAndProposer_Id(101L, 22L);
        verify(jobProposalRepository, never()).save(any(JobProposal.class));
    }

    @Test
    void nonOwnerCanReadOnlyTheirOwnProposal() {
        JobProposalService service = service();
        when(worker.getId()).thenReturn(22L);
        when(jobRepository.findByIdAndCreatedBy_Id(101L, 22L)).thenReturn(Optional.empty());
        when(jobProposalRepository.findByJob_IdAndProposer_Id(101L, 22L)).thenReturn(Optional.of(proposal));
        when(proposal.getJob()).thenReturn(job);
        when(job.getId()).thenReturn(101L);
        when(proposal.getProposer()).thenReturn(worker);
        when(proposal.getAmount()).thenReturn(new BigDecimal("30.00"));
        when(proposal.getStatus()).thenReturn(JobProposalStatus.SUBMITTED);

        List<JobProposalResponse> response = service.listVisible(101L, worker);

        assertEquals(1, response.size());
        verify(jobProposalRepository).findByJob_IdAndProposer_Id(101L, 22L);
        verify(jobProposalRepository, never()).findAllByJob_IdOrderByCreatedAtAscIdAsc(101L);
        verify(jobRepository, never()).findByIdAndStatusAndAssignmentMode(101L, JobStatus.OPEN, JobAssignmentMode.PROPOSALS);
        verify(jobRepository, never()).findById(101L);
    }

    @Test
    void terminalProposalJobIsHiddenFromUnrelatedListViewer() {
        JobProposalService service = service();
        when(worker.getId()).thenReturn(22L);
        when(jobRepository.findByIdAndCreatedBy_Id(101L, 22L)).thenReturn(Optional.empty());
        when(jobProposalRepository.findByJob_IdAndProposer_Id(101L, 22L)).thenReturn(Optional.empty());
        when(jobRepository.findByIdAndStatusAndAssignmentMode(
                101L,
                JobStatus.OPEN,
                JobAssignmentMode.PROPOSALS
        )).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.listVisible(101L, worker));

        verify(jobRepository, never()).findById(101L);
        verify(jobProposalRepository, never()).findAllByJob_IdOrderByCreatedAtAscIdAsc(101L);
    }

    @Test
    void ownerCanStillReadProposalHistoryAfterJobStopsBeingOpen() {
        JobProposalService service = service();
        when(requester.getId()).thenReturn(11L);
        when(jobRepository.findByIdAndCreatedBy_Id(101L, 11L)).thenReturn(Optional.of(job));
        when(job.getAssignmentMode()).thenReturn(JobAssignmentMode.PROPOSALS);
        when(jobProposalRepository.findAllByJob_IdOrderByCreatedAtAscIdAsc(101L)).thenReturn(List.of(proposal));
        when(proposal.getJob()).thenReturn(job);
        when(job.getId()).thenReturn(101L);
        when(proposal.getProposer()).thenReturn(worker);
        when(worker.getId()).thenReturn(22L);
        when(proposal.getStatus()).thenReturn(JobProposalStatus.ACCEPTED);

        List<JobProposalResponse> response = service.listVisible(101L, requester);

        assertEquals(1, response.size());
        verify(jobProposalRepository, never()).findByJob_IdAndProposer_Id(101L, 11L);
        verify(jobRepository, never()).findByIdAndStatusAndAssignmentMode(101L, JobStatus.OPEN, JobAssignmentMode.PROPOSALS);
    }

    @Test
    void higherProposalFundingQuoteUsesWalletBeforeStripe() {
        JobProposalService service = service();
        prepareProposalFundingQuote(new BigDecimal("42.00"));
        when(transactionService.getHeldAmount(job)).thenReturn(new BigDecimal("30.00"));
        when(walletService.getMyWallet(11L)).thenReturn(new WalletResponse(new BigDecimal("5.00")));

        JobProposalAcceptanceFundingResponse response = service.getAcceptanceFunding(101L, 55L, requester);

        assertEquals(new BigDecimal("30.00"), response.currentEscrowAmount());
        assertEquals(new BigDecimal("42.00"), response.targetEscrowAmount());
        assertEquals(new BigDecimal("5.00"), response.walletContributionAvailable());
        assertEquals(new BigDecimal("7.00"), response.paymentShortfall());
        assertEquals(new BigDecimal("7.00"), response.stripeChargeAmount());
        assertTrue(response.paymentRequired());
        assertTrue(response.onlinePaymentAvailable());
    }

    @Test
    void proposalFundingQuoteUsesOneZlotyMinimumWithoutOverfundingEscrow() {
        JobProposalService service = service();
        prepareProposalFundingQuote(new BigDecimal("42.00"));
        when(transactionService.getHeldAmount(job)).thenReturn(new BigDecimal("30.00"));
        when(walletService.getMyWallet(11L)).thenReturn(new WalletResponse(new BigDecimal("11.50")));

        JobProposalAcceptanceFundingResponse response = service.getAcceptanceFunding(101L, 55L, requester);

        assertEquals(new BigDecimal("0.50"), response.paymentShortfall());
        assertEquals(new BigDecimal("1.00"), response.stripeChargeAmount());
        assertTrue(response.paymentRequired());
        assertTrue(response.onlinePaymentAvailable());
    }

    @Test
    void oversizedProposalFundingQuoteDoesNotOfferUnsupportedSingleStripePayment() {
        JobProposalService service = service();
        prepareProposalFundingQuote(new BigDecimal("12042.00"));
        when(transactionService.getHeldAmount(job)).thenReturn(new BigDecimal("30.00"));
        when(walletService.getMyWallet(11L)).thenReturn(new WalletResponse(new BigDecimal("0.00")));

        JobProposalAcceptanceFundingResponse response = service.getAcceptanceFunding(101L, 55L, requester);

        assertEquals(new BigDecimal("12012.00"), response.paymentShortfall());
        assertFalse(response.onlinePaymentAvailable());
    }

    @Test
    void fundingQuoteRejectsNonOwnerBeforeProposalLedgerOrWalletReads() {
        JobProposalService service = service();
        when(worker.getId()).thenReturn(22L);
        when(jobRepository.findByIdAndCreatedBy_Id(101L, 22L)).thenReturn(Optional.empty());

        assertThrows(
                ForbiddenOperationException.class,
                () -> service.getAcceptanceFunding(101L, 55L, worker)
        );

        verify(jobRepository, never()).findById(101L);
        verify(jobProposalRepository, never()).findByIdAndJob_Id(55L, 101L);
        verify(transactionService, never()).getHeldAmount(any(Job.class));
        verify(walletService, never()).getMyWallet(22L);
    }

    @Test
    void blockedProposalCannotReachFundingOrWalletReads() {
        JobProposalService service = service();
        when(requester.getId()).thenReturn(11L);
        when(jobRepository.findByIdAndCreatedBy_Id(101L, 11L)).thenReturn(Optional.of(job));
        when(job.getAssignmentMode()).thenReturn(JobAssignmentMode.PROPOSALS);
        when(job.getCreatedBy()).thenReturn(requester);
        when(job.getStatus()).thenReturn(JobStatus.OPEN);
        when(jobProposalRepository.findByIdAndJob_Id(55L, 101L)).thenReturn(Optional.of(proposal));
        when(proposal.getStatus()).thenReturn(JobProposalStatus.SUBMITTED);
        when(proposal.getProposer()).thenReturn(worker);
        when(userBlockService.isInteractionBlocked(requester, worker)).thenReturn(true);

        assertThrows(
                ForbiddenOperationException.class,
                () -> service.getAcceptanceFunding(101L, 55L, requester)
        );

        verify(transactionService, never()).getHeldAmount(any(Job.class));
        verifyNoInteractions(walletService);
    }

    @Test
    void ownerGetsLifecycleConflictOnlyAfterOwnerScopedFundingLookup() {
        JobProposalService service = service();
        when(requester.getId()).thenReturn(11L);
        when(jobRepository.findByIdAndCreatedBy_Id(101L, 11L)).thenReturn(Optional.of(job));
        when(job.getAssignmentMode()).thenReturn(JobAssignmentMode.PROPOSALS);
        when(job.getCreatedBy()).thenReturn(requester);
        when(job.getStatus()).thenReturn(JobStatus.IN_PROGRESS);

        assertThrows(
                ConflictException.class,
                () -> service.getAcceptanceFunding(101L, 55L, requester)
        );

        verify(jobProposalRepository, never()).findByIdAndJob_Id(55L, 101L);
        verify(transactionService, never()).getHeldAmount(any(Job.class));
    }

    @Test
    void acceptedHigherProposalAdjustsEscrowBeforeAssigningWorker() {
        JobProposalService service = service();
        prepareOpenProposalJobForOwnerUpdate();
        when(worker.getId()).thenReturn(22L);
        when(jobProposalRepository.findByIdAndJob_Id(55L, 101L)).thenReturn(Optional.of(proposal));
        when(proposal.getStatus()).thenReturn(JobProposalStatus.SUBMITTED);
        when(proposal.getProposer()).thenReturn(worker);
        when(proposal.getAmount()).thenReturn(new BigDecimal("42.00"));
        when(proposal.getId()).thenReturn(55L);
        when(proposal.getJob()).thenReturn(job);
        when(jobProposalRepository.findAllByJob_IdAndStatusOrderByCreatedAtAscIdAsc(
                101L,
                JobProposalStatus.SUBMITTED
        )).thenReturn(List.of());
        when(jobRepository.save(job)).thenReturn(job);

        service.accept(101L, 55L, requester);

        var order = org.mockito.Mockito.inOrder(transactionService, job);
        order.verify(transactionService).adjustHeldAmount(job, new BigDecimal("42.00"), 55L);
        order.verify(job).setPrice(new BigDecimal("42.00"));
        order.verify(job).assignTo(any(User.class), any());
        verify(jobProposalRepository).save(proposal);
        verify(notificationService).notify(
                worker,
                NotificationType.JOB_PROPOSAL_ACCEPTED,
                "Twoja propozycja została zaakceptowana",
                "Zleceniodawca wybrał Twoją propozycję do „Zakupy”.",
                job,
                null
        );
    }

    @Test
    void acceptRejectsNonOwnerBeforeProposalOrEscrowReads() {
        JobProposalService service = service();
        when(worker.getId()).thenReturn(22L);
        when(jobRepository.findByIdAndCreatedByIdForUpdate(101L, 22L)).thenReturn(Optional.empty());

        assertThrows(
                ForbiddenOperationException.class,
                () -> service.accept(101L, 55L, worker)
        );

        verify(jobRepository, never()).findByIdForUpdate(101L);
        verify(jobProposalRepository, never()).findByIdAndJob_Id(55L, 101L);
        verify(transactionService, never()).adjustHeldAmount(any(), any(), any());
    }

    @Test
    void blockedProposalCannotBeAcceptedOrTriggerCommercialSideEffects() {
        JobProposalService service = service();
        when(requester.getId()).thenReturn(11L);
        when(jobRepository.findByIdAndCreatedByIdForUpdate(101L, 11L)).thenReturn(Optional.of(job));
        when(job.getAssignmentMode()).thenReturn(JobAssignmentMode.PROPOSALS);
        when(job.getStatus()).thenReturn(JobStatus.OPEN);
        when(job.getCreatedBy()).thenReturn(requester);
        when(jobProposalRepository.findByIdAndJob_Id(55L, 101L)).thenReturn(Optional.of(proposal));
        when(proposal.getStatus()).thenReturn(JobProposalStatus.SUBMITTED);
        when(proposal.getProposer()).thenReturn(worker);
        when(userBlockService.isInteractionBlocked(requester, worker)).thenReturn(true);

        assertThrows(ForbiddenOperationException.class, () -> service.accept(101L, 55L, requester));

        verify(transactionService, never()).adjustHeldAmount(any(), any(), any());
        verify(job, never()).setPrice(any());
        verify(job, never()).assignTo(any(), any());
        verify(proposal, never()).accept(any());
        verify(jobRepository, never()).save(job);
        verify(jobProposalRepository, never()).save(any(JobProposal.class));
        verify(jobProposalRepository, never()).saveAll(any());
        verifyNoInteractions(notificationService, liveTrackingService);
    }

    @Test
    void failedEscrowTopUpDoesNotAssignWorkerOrAcceptProposal() {
        JobProposalService service = service();
        prepareOpenProposalJobForOwnerUpdate();
        when(jobProposalRepository.findByIdAndJob_Id(56L, 101L)).thenReturn(Optional.of(proposal));
        when(proposal.getStatus()).thenReturn(JobProposalStatus.SUBMITTED);
        when(proposal.getProposer()).thenReturn(worker);
        when(proposal.getAmount()).thenReturn(new BigDecimal("50.00"));
        when(proposal.getId()).thenReturn(56L);
        org.mockito.Mockito.doThrow(new BusinessException("Brak środków na koncie"))
                .when(transactionService)
                .adjustHeldAmount(job, new BigDecimal("50.00"), 56L);

        assertThrows(BusinessException.class, () -> service.accept(101L, 56L, requester));

        verify(job, never()).setPrice(any());
        verify(job, never()).assignTo(any(), any());
        verify(proposal, never()).accept(any());
        verify(jobRepository, never()).save(job);
        verify(notificationService, never()).notify(any(), any(), anyString(), anyString(), any(), any());
    }

    @Test
    void withdrawRejectsNonOwnerBeforeTakingJobLock() {
        JobProposalService service = service();
        when(worker.getId()).thenReturn(22L);
        when(jobProposalRepository.existsByIdAndJob_IdAndProposer_Id(55L, 101L, 22L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> service.withdraw(101L, 55L, worker));

        verify(jobRepository, never()).findByIdForUpdate(101L);
        verify(jobProposalRepository, never()).findByIdAndJob_Id(55L, 101L);
    }

    @Test
    void proposerWithdrawKeepsExistingJobLockAndLifecycleChecks() {
        JobProposalService service = service();
        when(worker.getId()).thenReturn(22L);
        when(jobProposalRepository.existsByIdAndJob_IdAndProposer_Id(55L, 101L, 22L)).thenReturn(true);
        when(jobRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(job));
        when(job.getAssignmentMode()).thenReturn(JobAssignmentMode.PROPOSALS);
        when(jobProposalRepository.findByIdAndJob_Id(55L, 101L)).thenReturn(Optional.of(proposal));
        when(proposal.getProposer()).thenReturn(worker);
        when(proposal.getStatus()).thenReturn(JobProposalStatus.SUBMITTED);
        when(job.getStatus()).thenReturn(JobStatus.OPEN);

        service.withdraw(101L, 55L, worker);

        verify(proposal).withdraw(any());
        verify(jobProposalRepository).save(proposal);
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

    private void prepareOpenProposalJobForSubmit() {
        when(jobRepository.findByIdAndStatusAndAssignmentModeForUpdate(
                101L,
                JobStatus.OPEN,
                JobAssignmentMode.PROPOSALS
        )).thenReturn(Optional.of(job));
        lenient().when(job.getId()).thenReturn(101L);
        when(job.getCreatedBy()).thenReturn(requester);
        lenient().when(job.getPrice()).thenReturn(new BigDecimal("30.00"));
        lenient().when(job.getTitle()).thenReturn("Zakupy");
        when(userBlockService.isInteractionBlocked(requester, worker)).thenReturn(false);
    }

    private void prepareOpenProposalJobForOwnerUpdate() {
        when(requester.getId()).thenReturn(11L);
        when(jobRepository.findByIdAndCreatedByIdForUpdate(101L, 11L)).thenReturn(Optional.of(job));
        lenient().when(job.getId()).thenReturn(101L);
        when(job.getAssignmentMode()).thenReturn(JobAssignmentMode.PROPOSALS);
        when(job.getStatus()).thenReturn(JobStatus.OPEN);
        when(job.getCreatedBy()).thenReturn(requester);
        lenient().when(job.getPrice()).thenReturn(new BigDecimal("30.00"));
        lenient().when(job.getTitle()).thenReturn("Zakupy");
        when(userBlockService.isInteractionBlocked(requester, worker)).thenReturn(false);
    }

    private void prepareProposalFundingQuote(BigDecimal proposalAmount) {
        when(requester.getId()).thenReturn(11L);
        when(jobRepository.findByIdAndCreatedBy_Id(101L, 11L)).thenReturn(Optional.of(job));
        when(job.getId()).thenReturn(101L);
        when(job.getAssignmentMode()).thenReturn(JobAssignmentMode.PROPOSALS);
        when(job.getStatus()).thenReturn(JobStatus.OPEN);
        when(job.getCreatedBy()).thenReturn(requester);
        when(jobProposalRepository.findByIdAndJob_Id(55L, 101L)).thenReturn(Optional.of(proposal));
        when(proposal.getStatus()).thenReturn(JobProposalStatus.SUBMITTED);
        when(proposal.getProposer()).thenReturn(worker);
        when(proposal.getAmount()).thenReturn(proposalAmount);
        when(proposal.getId()).thenReturn(55L);
        when(userBlockService.isInteractionBlocked(requester, worker)).thenReturn(false);
    }
}
