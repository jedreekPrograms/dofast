package com.doFast.dofastapp.payment.service;

import com.doFast.dofastapp.common.enums.TransactionStatus;
import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.payment.dto.FinanceReconciliationResponse;
import com.doFast.dofastapp.payment.fee.PlatformRevenueEntryRepository;
import com.doFast.dofastapp.payment.fee.PlatformRevenueType;
import com.doFast.dofastapp.payment.repository.PaymentTransactionRepository;
import com.doFast.dofastapp.payment.repository.TransactionRepository;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.enums.UserRole;
import com.doFast.dofastapp.wallet.repository.WalletTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinanceReconciliationServiceTest {

    @Mock private WalletTransactionRepository walletTransactionRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private PaymentTransactionRepository paymentTransactionRepository;
    @Mock private PlatformRevenueEntryRepository platformRevenueEntryRepository;

    private FinanceReconciliationService reconciliationService;
    private User admin;

    @BeforeEach
    void setUp() {
        reconciliationService = new FinanceReconciliationService(
                walletTransactionRepository,
                transactionRepository,
                paymentTransactionRepository,
                platformRevenueEntryRepository
        );
        admin = new User("admin@example.com", "admin");
        admin.setRole(UserRole.ADMIN);
        ReflectionTestUtils.setField(admin, "id", 9L);
        lenient().when(transactionRepository.sumAmountByStatus(TransactionStatus.HELD))
                .thenReturn(BigDecimal.ZERO.setScale(2));
        lenient().when(platformRevenueEntryRepository.sumAmountByType(PlatformRevenueType.PLATFORM_FEE))
                .thenReturn(BigDecimal.ZERO.setScale(2));
    }

    @Test
    void transientAdminFailsClosedBeforeReadingFinancialState() {
        User transientAdmin = new User("transient-admin@example.com", "transient-admin");
        transientAdmin.setRole(UserRole.ADMIN);

        assertThrows(ForbiddenOperationException.class,
                () -> reconciliationService.reconcile(transientAdmin));

        verifyNoInteractions(
                walletTransactionRepository,
                transactionRepository,
                paymentTransactionRepository,
                platformRevenueEntryRepository
        );
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

        FinanceReconciliationResponse report = reconciliationService.reconcile(admin);

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

        FinanceReconciliationResponse report = reconciliationService.reconcile(admin);

        assertFalse(report.healthy());
        assertEquals(1L, report.platformRevenueMismatches());
    }

    @Test
    void stripeLedgerMismatchMarksReportUnhealthy() {
        when(paymentTransactionRepository.countStripeLedgerMismatches()).thenReturn(1L);

        FinanceReconciliationResponse report = reconciliationService.reconcile(admin);

        assertFalse(report.healthy());
        assertEquals(1L, report.stripeLedgerMismatches());
    }
}
