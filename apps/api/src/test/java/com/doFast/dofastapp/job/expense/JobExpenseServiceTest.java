package com.doFast.dofastapp.job.expense;

import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
import com.doFast.dofastapp.job.attachment.JobAttachment;
import com.doFast.dofastapp.job.attachment.JobAttachmentRepository;
import com.doFast.dofastapp.job.attachment.JobAttachmentVisibility;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.repository.JobRepository;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.wallet.enums.WalletTransactionType;
import com.doFast.dofastapp.wallet.service.WalletService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobExpenseServiceTest {

    @Mock private JobRepository jobRepository;
    @Mock private JobExpenseEscrowRepository escrowRepository;
    @Mock private JobExpenseClaimRepository claimRepository;
    @Mock private JobAttachmentRepository attachmentRepository;
    @Mock private WalletService walletService;
    @Mock private Job job;
    @Mock private User requester;
    @Mock private User worker;
    @Mock private JobAttachment receipt;

    @Test
    void holdsExpenseBudgetInItsOwnWalletLedgerType() {
        JobExpenseService service = service();
        when(job.getId()).thenReturn(71L);
        when(job.getCreatedBy()).thenReturn(requester);
        when(job.getExpenseBudget()).thenReturn(new BigDecimal("120.00"));
        when(requester.getId()).thenReturn(11L);
        when(escrowRepository.findByJob_Id(71L)).thenReturn(Optional.empty());
        when(walletService.debit(
                11L,
                new BigDecimal("120.00"),
                WalletTransactionType.EXPENSE_BUDGET_LOCK,
                71L,
                "job:71:expense:lock"
        )).thenReturn(true);

        service.holdBudget(job);

        verify(walletService).debit(11L, new BigDecimal("120.00"), WalletTransactionType.EXPENSE_BUDGET_LOCK,
                71L, "job:71:expense:lock");
        verify(escrowRepository).save(any(JobExpenseEscrow.class));
    }

    @Test
    void refusesToCreateExpenseEscrowWhenWalletLockWasNotApplied() {
        JobExpenseService service = service();
        when(job.getId()).thenReturn(79L);
        when(job.getCreatedBy()).thenReturn(requester);
        when(job.getExpenseBudget()).thenReturn(new BigDecimal("90.00"));
        when(requester.getId()).thenReturn(19L);
        when(escrowRepository.findByJob_Id(79L)).thenReturn(Optional.empty());
        when(walletService.debit(
                19L,
                new BigDecimal("90.00"),
                WalletTransactionType.EXPENSE_BUDGET_LOCK,
                79L,
                "job:79:expense:lock"
        )).thenReturn(false);

        assertThrows(ConflictException.class, () -> service.holdBudget(job));

        verify(escrowRepository, never()).save(any(JobExpenseEscrow.class));
    }

    @Test
    void completionReimbursesClaimedAmountAndRestoresUnusedBudgetSourcesFeeFree() {
        JobExpenseService service = service();
        when(job.getId()).thenReturn(72L);
        when(job.getCreatedBy()).thenReturn(requester);
        when(job.getTakenBy()).thenReturn(worker);
        when(requester.getId()).thenReturn(12L);
        when(worker.getId()).thenReturn(22L);
        JobExpenseEscrow escrow = new JobExpenseEscrow(job, requester, new BigDecimal("100.00"), java.time.LocalDateTime.now());
        escrow.addClaim(new BigDecimal("35.00"));
        when(escrowRepository.findByJobIdForUpdate(72L)).thenReturn(Optional.of(escrow));
        when(walletService.credit(
                22L,
                new BigDecimal("35.00"),
                WalletTransactionType.EXPENSE_REIMBURSEMENT,
                72L,
                "job:72:expense:reimburse"
        )).thenReturn(true);
        when(walletService.creditRestoringJobDebits(
                12L,
                new BigDecimal("65.00"),
                WalletTransactionType.EXPENSE_BUDGET_REFUND,
                72L,
                "job:72:expense:refund",
                Set.of(WalletTransactionType.EXPENSE_BUDGET_LOCK)
        )).thenReturn(true);

        service.settleOnCompletion(job);

        verify(walletService).credit(22L, new BigDecimal("35.00"), WalletTransactionType.EXPENSE_REIMBURSEMENT,
                72L, "job:72:expense:reimburse");
        verify(walletService).creditRestoringJobDebits(
                12L,
                new BigDecimal("65.00"),
                WalletTransactionType.EXPENSE_BUDGET_REFUND,
                72L,
                "job:72:expense:refund",
                Set.of(WalletTransactionType.EXPENSE_BUDGET_LOCK)
        );
        assertEquals(JobExpenseEscrowStatus.SETTLED, escrow.getStatus());
        assertEquals(new BigDecimal("35.00"), escrow.getReimbursedAmount());
        assertEquals(new BigDecimal("65.00"), escrow.getRefundedAmount());
    }

    @Test
    void refusesToSettleExpenseEscrowWhenWorkerReimbursementWasNotApplied() {
        JobExpenseService service = service();
        when(job.getId()).thenReturn(80L);
        when(job.getCreatedBy()).thenReturn(requester);
        when(job.getTakenBy()).thenReturn(worker);
        when(worker.getId()).thenReturn(30L);
        JobExpenseEscrow escrow = new JobExpenseEscrow(job, requester, new BigDecimal("50.00"), java.time.LocalDateTime.now());
        escrow.addClaim(new BigDecimal("20.00"));
        when(escrowRepository.findByJobIdForUpdate(80L)).thenReturn(Optional.of(escrow));
        when(walletService.credit(
                30L,
                new BigDecimal("20.00"),
                WalletTransactionType.EXPENSE_REIMBURSEMENT,
                80L,
                "job:80:expense:reimburse"
        )).thenReturn(false);

        assertThrows(ConflictException.class, () -> service.settleOnCompletion(job));

        assertEquals(JobExpenseEscrowStatus.HELD, escrow.getStatus());
        verify(escrowRepository, never()).save(escrow);
        verify(walletService, never()).creditRestoringJobDebits(any(), any(), any(), any(), any(), any());
    }

    @Test
    void disputeCanApproveOnlyPartOfClaimedExpenses() {
        JobExpenseService service = service();
        when(job.getId()).thenReturn(75L);
        when(job.getCreatedBy()).thenReturn(requester);
        when(job.getTakenBy()).thenReturn(worker);
        when(requester.getId()).thenReturn(15L);
        when(worker.getId()).thenReturn(25L);
        JobExpenseEscrow escrow = new JobExpenseEscrow(job, requester, new BigDecimal("100.00"), java.time.LocalDateTime.now());
        escrow.addClaim(new BigDecimal("40.00"));
        when(escrowRepository.findByJobIdForUpdate(75L)).thenReturn(Optional.of(escrow));
        when(walletService.credit(
                25L,
                new BigDecimal("25.00"),
                WalletTransactionType.EXPENSE_REIMBURSEMENT,
                75L,
                "job:75:expense:reimburse"
        )).thenReturn(true);
        when(walletService.creditRestoringJobDebits(
                15L,
                new BigDecimal("75.00"),
                WalletTransactionType.EXPENSE_BUDGET_REFUND,
                75L,
                "job:75:expense:refund",
                Set.of(WalletTransactionType.EXPENSE_BUDGET_LOCK)
        )).thenReturn(true);

        service.settleForDispute(job, new BigDecimal("25.00"));

        verify(walletService).credit(25L, new BigDecimal("25.00"), WalletTransactionType.EXPENSE_REIMBURSEMENT,
                75L, "job:75:expense:reimburse");
        verify(walletService).creditRestoringJobDebits(
                15L,
                new BigDecimal("75.00"),
                WalletTransactionType.EXPENSE_BUDGET_REFUND,
                75L,
                "job:75:expense:refund",
                Set.of(WalletTransactionType.EXPENSE_BUDGET_LOCK)
        );
        assertEquals(JobExpenseEscrowStatus.SETTLED, escrow.getStatus());
        assertEquals(new BigDecimal("25.00"), escrow.getReimbursedAmount());
        assertEquals(new BigDecimal("75.00"), escrow.getRefundedAmount());
    }

    @Test
    void disputeCannotApproveMoreThanClaimedBeforeMovingMoney() {
        JobExpenseService service = service();
        when(job.getId()).thenReturn(76L);
        JobExpenseEscrow escrow = new JobExpenseEscrow(job, requester, new BigDecimal("100.00"), java.time.LocalDateTime.now());
        escrow.addClaim(new BigDecimal("40.00"));
        when(escrowRepository.findByJobIdForUpdate(76L)).thenReturn(Optional.of(escrow));

        assertThrows(ConflictException.class,
                () -> service.settleForDispute(job, new BigDecimal("40.01")));

        verify(walletService, never()).credit(any(), any(), any(), any(), any());
        verify(walletService, never()).creditRestoringJobDebits(any(), any(), any(), any(), any(), any());
        assertEquals(JobExpenseEscrowStatus.HELD, escrow.getStatus());
    }

    @Test
    void refundAllDoesNotMarkEscrowRefundedWhenWalletRefundWasNotApplied() {
        JobExpenseService service = service();
        when(job.getId()).thenReturn(81L);
        when(job.getCreatedBy()).thenReturn(requester);
        when(requester.getId()).thenReturn(41L);
        JobExpenseEscrow escrow = new JobExpenseEscrow(job, requester, new BigDecimal("70.00"), java.time.LocalDateTime.now());
        when(escrowRepository.findByJobIdForUpdate(81L)).thenReturn(Optional.of(escrow));
        when(walletService.creditRestoringJobDebits(
                41L,
                new BigDecimal("70.00"),
                WalletTransactionType.EXPENSE_BUDGET_REFUND,
                81L,
                "job:81:expense:refund",
                Set.of(WalletTransactionType.EXPENSE_BUDGET_LOCK)
        )).thenReturn(false);

        assertThrows(ConflictException.class, () -> service.refundAll(job));

        assertEquals(JobExpenseEscrowStatus.HELD, escrow.getStatus());
        verify(escrowRepository, never()).save(escrow);
    }

    @Test
    void claimRequiresPrivateParticipantReceiptUploadedByAssignedWorker() {
        JobExpenseService service = service();
        when(worker.getId()).thenReturn(23L);
        when(jobRepository.findAssignedWorkerByIdForUpdate(73L, 23L)).thenReturn(Optional.of(job));
        when(job.getStatus()).thenReturn(JobStatus.IN_PROGRESS);
        when(job.getTakenBy()).thenReturn(worker);
        JobExpenseEscrow escrow = new JobExpenseEscrow(job, requester, new BigDecimal("80.00"), java.time.LocalDateTime.now());
        when(escrowRepository.findByJobIdForUpdate(73L)).thenReturn(Optional.of(escrow));
        when(attachmentRepository.findByIdAndJob_IdAndDeletedAtIsNull(900L, 73L)).thenReturn(Optional.of(receipt));
        when(receipt.getVisibility()).thenReturn(JobAttachmentVisibility.JOB_VIEWERS);

        assertThrows(ConflictException.class,
                () -> service.createClaim(73L, new CreateJobExpenseClaimRequest(new BigDecimal("20.00"), 900L), worker));

        verify(claimRepository, never()).save(any());
        assertEquals(new BigDecimal("0.00"), escrow.getClaimedAmount());
    }

    @Test
    void claimRejectsNonReceiptMediaBeforeMutatingEscrow() {
        JobExpenseService service = service();
        when(worker.getId()).thenReturn(24L);
        when(jobRepository.findAssignedWorkerByIdForUpdate(74L, 24L)).thenReturn(Optional.of(job));
        when(job.getStatus()).thenReturn(JobStatus.IN_PROGRESS);
        when(job.getTakenBy()).thenReturn(worker);
        JobExpenseEscrow escrow = new JobExpenseEscrow(job, requester, new BigDecimal("80.00"), java.time.LocalDateTime.now());
        when(escrowRepository.findByJobIdForUpdate(74L)).thenReturn(Optional.of(escrow));
        when(attachmentRepository.findByIdAndJob_IdAndDeletedAtIsNull(901L, 74L)).thenReturn(Optional.of(receipt));
        when(receipt.getVisibility()).thenReturn(JobAttachmentVisibility.PARTICIPANTS);
        when(receipt.getUploadedBy()).thenReturn(worker);
        when(receipt.getMediaType()).thenReturn("text/plain");

        assertThrows(ConflictException.class,
                () -> service.createClaim(74L, new CreateJobExpenseClaimRequest(new BigDecimal("20.00"), 901L), worker));

        verify(claimRepository, never()).save(any());
        verify(escrowRepository, never()).save(any());
        assertEquals(new BigDecimal("0.00"), escrow.getClaimedAmount());
    }

    @Test
    void outsiderCannotEnumeratePrivateExpenseSummary() {
        JobExpenseService service = service();
        when(requester.getId()).thenReturn(91L);
        when(jobRepository.findParticipantById(73L, 91L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getSummary(73L, requester));

        verify(escrowRepository, never()).findByJob_Id(73L);
        verify(claimRepository, never()).findAllByJob_IdOrderByCreatedAtAscIdAsc(73L);
        verify(jobRepository, never()).findById(73L);
    }

    @Test
    void participantSummaryUsesScopedLookupBeforeReadingFinancialEvidence() {
        JobExpenseService service = service();
        when(requester.getId()).thenReturn(11L);
        when(jobRepository.findParticipantById(77L, 11L)).thenReturn(Optional.of(job));
        when(escrowRepository.findByJob_Id(77L)).thenReturn(Optional.empty());

        JobExpenseSummaryResponse summary = service.getSummary(77L, requester);

        assertEquals(77L, summary.jobId());
        assertEquals(new BigDecimal("0.00"), summary.budgetAmount());
        verify(jobRepository).findParticipantById(77L, 11L);
        verify(jobRepository, never()).findById(77L);
    }

    @Test
    void outsiderCannotEnumerateOrCreateExpenseClaim() {
        JobExpenseService service = service();
        when(worker.getId()).thenReturn(99L);
        when(jobRepository.findAssignedWorkerByIdForUpdate(78L, 99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.createClaim(78L, new CreateJobExpenseClaimRequest(new BigDecimal("20.00"), 902L), worker));

        verify(jobRepository, never()).findByIdForUpdate(78L);
        verify(escrowRepository, never()).findByJobIdForUpdate(78L);
        verify(attachmentRepository, never()).findByIdAndJob_IdAndDeletedAtIsNull(902L, 78L);
        verify(claimRepository, never()).save(any());
        verify(walletService, never()).credit(any(), any(), any(), any(), any());
        verify(walletService, never()).debit(any(), any(), any(), any(), any());
    }

    private JobExpenseService service() {
        return new JobExpenseService(jobRepository, escrowRepository, claimRepository, attachmentRepository, walletService);
    }
}
