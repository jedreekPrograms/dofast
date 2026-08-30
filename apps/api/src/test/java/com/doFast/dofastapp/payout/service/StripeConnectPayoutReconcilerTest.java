package com.doFast.dofastapp.payout.service;

import com.doFast.dofastapp.payout.provider.PayoutSubmittedReconciliationCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StripeConnectPayoutReconcilerTest {

    @Mock private PayoutSubmittedReconciliationQueue queue;
    @Mock private StripeConnectPayoutReconciliationService reconciliationService;

    private StripeConnectPayoutReconciler reconciler;
    private PayoutSubmittedReconciliationCommand command;

    @BeforeEach
    void setUp() {
        reconciler = new StripeConnectPayoutReconciler(queue, reconciliationService);
        command = new PayoutSubmittedReconciliationCommand(
                41L,
                7L,
                new BigDecimal("125.00"),
                "PLN",
                "stripe-connect",
                "po_123",
                "tr_123"
        );
    }

    @Test
    void pendingProviderStateClearsTransientReconciliationError() {
        when(queue.claimNext("stripe-connect")).thenReturn(Optional.of(command), Optional.empty());
        when(reconciliationService.reconcile(command))
                .thenReturn(StripeConnectPayoutReconciliationService.Outcome.PENDING);

        reconciler.reconcile();

        verify(queue).recordProviderHealthy(41L);
        verify(queue, never()).recordProviderFailure(41L, "STRIPE_RECONCILIATION_ERROR");
    }

    @Test
    void terminalProviderStateDoesNotScheduleAnyLocalRetryOrWalletAction() {
        when(queue.claimNext("stripe-connect")).thenReturn(Optional.of(command), Optional.empty());
        when(reconciliationService.reconcile(command))
                .thenReturn(StripeConnectPayoutReconciliationService.Outcome.TERMINAL);

        reconciler.reconcile();

        verify(queue, never()).recordProviderHealthy(41L);
        verify(queue, never()).recordProviderFailure(41L, "STRIPE_RECONCILIATION_ERROR");
    }

    @Test
    void providerExceptionOnlyRecordsReconciliationFailureAndContinuesFailClosed() {
        when(queue.claimNext("stripe-connect")).thenReturn(Optional.of(command), Optional.empty());
        when(reconciliationService.reconcile(command)).thenThrow(new RuntimeException("provider unavailable"));

        reconciler.reconcile();

        verify(queue).recordProviderFailure(41L, "STRIPE_RECONCILIATION_ERROR");
        verify(queue, never()).recordProviderHealthy(41L);
    }
}
