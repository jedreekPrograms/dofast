package com.doFast.dofastapp.payment.service;

import com.doFast.dofastapp.common.enums.TransactionStatus;
import com.doFast.dofastapp.payment.dto.FinanceReconciliationResponse;
import com.doFast.dofastapp.payment.fee.PlatformRevenueEntryRepository;
import com.doFast.dofastapp.payment.fee.PlatformRevenueType;
import com.doFast.dofastapp.payment.repository.PaymentTransactionRepository;
import com.doFast.dofastapp.payment.repository.TransactionRepository;
import com.doFast.dofastapp.wallet.repository.WalletTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinanceReconciliationServiceTest {

    @Mock private WalletTransactionRepository walletTransactionRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private PaymentTransactionRepository paymentTransactionRepository;
    @Mock private PlatformRevenueEntryRepository platformRevenueEntryRepository;

    private FinanceReconciliationService reconciliationService;

    @BeforeEach
    void setUp() {
        reconciliationService = new FinanceReconciliationService(
                walletTransactionRepository,
                transactionRepository,
                paymentTransactionRepository,
                platformRevenueEntryRepository
        );
        when(transactionRepository.sumAmountByStatus(TransactionStatus.HELD))
                .thenReturn(BigDecimal.ZERO.setScale(2));
        when(platformRevenueEntryRepository.sumAmountByType(PlatformRevenueType.PLATFORM_FEE))
                .thenReturn(BigDecimal.ZERO.setScale(2));
    }

    @Test
    void healthyReportRequiresEveryIntegrityCheckToPass() {
        when(walletTransactionRepository.countWalletBalanceMismatches()).thenReturn(0L);
        when(walletTransactionRepository.countLedgerSequenceMismatches()).thenReturn(0L);
        when(paymentTransactionRepository.countStripeLedgerMismatches()).thenReturn(0L);
        when(platformRevenueEntryRepository.countSettlementMismatches()).thenReturn(0L);
        when(transactionRepository.countByStatus(TransactionStatus.HELD)).thenReturn(2L);
        when(transactionRepository.sumAmountByStatus(TransactionStatus.HELD))
                .thenReturn(new BigDecimal("45.00"));
        when(platformRevenueEntryRepository.sumAmountByType(PlatformRevenueType.PLATFORM_FEE))
                .thenReturn(new BigDecimal("3.21"));
        when(paymentTransactionRepository.count()).thenReturn(7L);

        FinanceReconciliationResponse report = reconciliationService.reconcile();

        assertTrue(report.healthy());
        assertEquals(0L, report.walletBalanceMismatches());
        assertEquals(0L, report.ledgerSequenceMismatches());
        assertEquals(0L, report.stripeLedgerMismatches());
        assertEquals(0L, report.platformRevenueMismatches());
        assertEquals(2L, report.heldEscrowCount());
        assertEquals(new BigDecimal("45.00"), report.heldEscrowAmount());
        assertEquals(new BigDecimal("3.21"), report.platformFeeRevenueAmount());
        assertEquals(7L, report.processedStripePayments());
    }

    @Test
    void platformRevenueMismatchMarksReportUnhealthy() {
        when(platformRevenueEntryRepository.countSettlementMismatches()).thenReturn(1L);

        FinanceReconciliationResponse report = reconciliationService.reconcile();

        assertFalse(report.healthy());
        assertEquals(1L, report.platformRevenueMismatches());
    }

    @Test
    void stripeLedgerMismatchMarksReportUnhealthy() {
        when(paymentTransactionRepository.countStripeLedgerMismatches()).thenReturn(1L);

        FinanceReconciliationResponse report = reconciliationService.reconcile();

        assertFalse(report.healthy());
        assertEquals(1L, report.stripeLedgerMismatches());
    }
}
