package com.doFast.dofastapp.job.expense;

import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.common.exception.ConflictException;
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

        service.holdBudget(job);

        verify(walletService).debit(11L, new BigDecimal("120.00"), WalletTransactionType.EXPENSE_BUDGET_LOCK,
                71L, "job:71:expense:lock");
        verify(escrowRepository).save(any(JobExpenseEscrow.class));
    }

    @Test
    void completionReimbursesClaimedAmountAndRefundsUnusedBudgetFeeFree() {
        JobExpenseService service = service();
        when(job.getId()).thenReturn(72L);
        when(job.getCreatedBy()).thenReturn(requester);
        when(job.getTakenBy()).thenReturn(worker);
        when(requester.getId()).thenReturn(12L);
        when(worker.getId()).thenReturn(22L);
        JobExpenseEscrow escrow = new JobExpenseEscrow(job, requester, new BigDecimal("100.00"), java.time.LocalDateTime.now());
        escrow.addClaim(new BigDecimal("35.00"));
        when(escrowRepository.findByJobIdForUpdate(72L)).thenReturn(Optional.of(escrow));

        service.settleOnCompletion(job);

        verify(walletService).credit(22L, new BigDecimal("35.00"), WalletTransactionType.EXPENSE_REIMBURSEMENT,
                72L, "job:72:expense:reimburse");
        verify(walletService).credit(12L, new BigDecimal("65.00"), WalletTransactionType.EXPENSE_BUDGET_REFUND,
                72L, "job:72:expense:refund");
        assertEquals(JobExpenseEscrowStatus.SETTLED, escrow.getStatus());
        assertEquals(new BigDecimal("35.00"), escrow.getReimbursedAmount());
        assertEquals(new BigDecimal("65.00"), escrow.getRefundedAmount());
    }

    @Test
    void claimRequiresPrivateParticipantReceiptUploadedByAssignedWorker() {
        JobExpenseService service = service();
        when(jobRepository.findByIdForUpdate(73L)).thenReturn(Optional.of(job));
        when(job.getStatus()).thenReturn(JobStatus.IN_PROGRESS);
        when(job.getTakenBy()).thenReturn(worker);
        when(worker.getId()).thenReturn(23L);
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
        when(jobRepository.findByIdForUpdate(74L)).thenReturn(Optional.of(job));
        when(job.getStatus()).thenReturn(JobStatus.IN_PROGRESS);
        when(job.getTakenBy()).thenReturn(worker);
        when(worker.getId()).thenReturn(24L);
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

    private JobExpenseService service() {
        return new JobExpenseService(jobRepository, escrowRepository, claimRepository, attachmentRepository, walletService);
    }
}
