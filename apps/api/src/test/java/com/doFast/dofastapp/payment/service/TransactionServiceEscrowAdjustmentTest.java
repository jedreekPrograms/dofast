package com.doFast.dofastapp.payment.service;

import com.doFast.dofastapp.common.enums.TransactionStatus;
import com.doFast.dofastapp.common.exception.BusinessException;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.payment.entity.Transaction;
import com.doFast.dofastapp.payment.fee.PlatformFeePolicy;
import com.doFast.dofastapp.payment.fee.PlatformRevenueService;
import com.doFast.dofastapp.payment.repository.TransactionRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceEscrowAdjustmentTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private WalletService walletService;
    @Mock private PlatformFeePolicy platformFeePolicy;
    @Mock private PlatformRevenueService platformRevenueService;
    @Mock private Transaction transaction;
    @Mock private Job job;
    @Mock private User payer;

    @Test
    void exposesHeldAmountWithoutMutatingLedger() {
        when(job.getCreatedBy()).thenReturn(payer);
        when(payer.getId()).thenReturn(7L);
        when(transactionRepository.findByJob(job)).thenReturn(Optional.of(transaction));
        when(transaction.getStatus()).thenReturn(TransactionStatus.HELD);
        when(transaction.getPayer()).thenReturn(payer);
        when(transaction.getAmount()).thenReturn(new BigDecimal("30.00"));
        TransactionService service = new TransactionService(
                transactionRepository,
                walletService,
                platformFeePolicy,
                platformRevenueService
        );

        assertEquals(new BigDecimal("30.00"), service.getHeldAmount(job));

        verify(walletService, never()).debit(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
        verify(walletService, never()).credit(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void locksOnlyTheAdditionalDeltaWhenAcceptedProposalIsHigher() {
        TransactionService service = serviceWithHeldEscrow("30.00");

        service.adjustHeldAmount(job, new BigDecimal("42.00"), 9L);

        verify(walletService).debit(
                7L,
                new BigDecimal("12.00"),
                WalletTransactionType.ESCROW_ADJUSTMENT_LOCK,
                101L,
                "escrow:101:proposal:9:adjust:lock"
        );
        verify(transaction).adjustHeldAmount(new BigDecimal("42.00"));
        verify(transactionRepository).save(transaction);
    }

    @Test
    void refundsOnlyTheExcessWhenAcceptedProposalIsLower() {
        TransactionService service = serviceWithHeldEscrow("30.00");

        service.adjustHeldAmount(job, new BigDecimal("25.00"), 10L);

        verify(walletService).credit(
                7L,
                new BigDecimal("5.00"),
                WalletTransactionType.ESCROW_ADJUSTMENT_REFUND,
                101L,
                "escrow:101:proposal:10:adjust:refund"
        );
        verify(transaction).adjustHeldAmount(new BigDecimal("25.00"));
        verify(transactionRepository).save(transaction);
    }

    @Test
    void insufficientDeltaLeavesEscrowAmountUntouched() {
        TransactionService service = serviceWithHeldEscrow("30.00");
        when(walletService.debit(
                7L,
                new BigDecimal("20.00"),
                WalletTransactionType.ESCROW_ADJUSTMENT_LOCK,
                101L,
                "escrow:101:proposal:11:adjust:lock"
        )).thenThrow(new BusinessException("Brak środków na koncie"));

        assertThrows(
                BusinessException.class,
                () -> service.adjustHeldAmount(job, new BigDecimal("50.00"), 11L)
        );

        verify(transaction, never()).adjustHeldAmount(new BigDecimal("50.00"));
        verify(transactionRepository, never()).save(transaction);
    }

    private TransactionService serviceWithHeldEscrow(String amount) {
        when(job.getId()).thenReturn(101L);
        when(job.getCreatedBy()).thenReturn(payer);
        when(payer.getId()).thenReturn(7L);
        when(transactionRepository.findByJobForUpdate(job)).thenReturn(Optional.of(transaction));
        when(transaction.getStatus()).thenReturn(TransactionStatus.HELD);
        when(transaction.getPayer()).thenReturn(payer);
        when(transaction.getAmount()).thenReturn(new BigDecimal(amount));
        return new TransactionService(
                transactionRepository,
                walletService,
                platformFeePolicy,
                platformRevenueService
        );
    }
}
