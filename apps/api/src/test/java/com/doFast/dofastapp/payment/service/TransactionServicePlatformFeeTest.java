package com.doFast.dofastapp.payment.service;

import com.doFast.dofastapp.common.enums.TransactionStatus;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServicePlatformFeeTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private WalletService walletService;
    @Mock private PlatformRevenueService platformRevenueService;
    @Mock private Job job;
    @Mock private User payer;
    @Mock private User payee;
    @Mock private Transaction transaction;

    @Test
    void snapshotsCurrentFeeRateWhenEscrowIsCreated() {
        TransactionService service = new TransactionService(
                transactionRepository,
                walletService,
                new PlatformFeePolicy(100),
                platformRevenueService
        );
        when(job.getId()).thenReturn(101L);
        when(job.getCreatedBy()).thenReturn(payer);
        when(job.getPrice()).thenReturn(new BigDecimal("30.00"));
        when(payer.getId()).thenReturn(7L);
        when(transactionRepository.findByJobForUpdate(job)).thenReturn(Optional.empty());
        when(walletService.debit(
                7L,
                new BigDecimal("30.00"),
                WalletTransactionType.ESCROW_LOCK,
                101L,
                "escrow:101:lock"
        )).thenReturn(true);

        service.holdMoney(job);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        Transaction held = captor.getValue();
        assertEquals(TransactionStatus.HELD, held.getStatus());
        assertEquals(new BigDecimal("30.00"), held.getAmount());
        assertEquals(100, held.getPlatformFeeBasisPoints());
    }

    @Test
    void releasesNetPayoutAndRecordsFeeFromEscrowSnapshot() {
        TransactionService service = new TransactionService(
                transactionRepository,
                walletService,
                new PlatformFeePolicy(250),
                platformRevenueService
        );
        when(job.getId()).thenReturn(101L);
        when(transactionRepository.findByJobForUpdate(job)).thenReturn(Optional.of(transaction));
        when(transaction.getStatus()).thenReturn(TransactionStatus.HELD);
        when(transaction.getAmount()).thenReturn(new BigDecimal("30.00"));
        when(transaction.getPlatformFeeBasisPoints()).thenReturn(100);
        when(payee.getId()).thenReturn(8L);
        when(walletService.credit(
                8L,
                new BigDecimal("29.70"),
                WalletTransactionType.ESCROW_RELEASE,
                101L,
                "escrow:101:release"
        )).thenReturn(true);

        service.releaseMoney(job, payee);

        verify(platformRevenueService).recordPlatformFee(transaction, job, new BigDecimal("0.30"));
        verify(transaction).releaseTo(
                eq(payee),
                eq(new BigDecimal("0.30")),
                eq(new BigDecimal("29.70")),
                any(LocalDateTime.class)
        );
        verify(transactionRepository).save(transaction);
    }
}
