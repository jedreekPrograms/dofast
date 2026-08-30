package com.doFast.dofastapp.payout.service;

import com.doFast.dofastapp.payout.provider.PayoutSubmittedReconciliationCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "dofast.payouts.stripe-connect",
        name = "reconciliation-enabled",
        havingValue = "true"
)
public class StripeConnectPayoutReconciler {

    private static final Logger log = LoggerFactory.getLogger(StripeConnectPayoutReconciler.class);
    private static final int MAX_PER_TICK = 10;

    private final PayoutSubmittedReconciliationQueue queue;
    private final StripeConnectPayoutReconciliationService reconciliationService;

    public StripeConnectPayoutReconciler(
            PayoutSubmittedReconciliationQueue queue,
            StripeConnectPayoutReconciliationService reconciliationService
    ) {
        this.queue = queue;
        this.reconciliationService = reconciliationService;
    }

    @Scheduled(fixedDelayString = "${dofast.payouts.stripe-connect.reconciliation-interval-ms:60000}")
    public void reconcile() {
        for (int index = 0; index < MAX_PER_TICK; index++) {
            var claim = queue.claimNext(StripeConnectOnboardingService.PROVIDER_CODE);
            if (claim.isEmpty()) return;

            PayoutSubmittedReconciliationCommand command = claim.get();
            try {
                StripeConnectPayoutReconciliationService.Outcome outcome = reconciliationService.reconcile(command);
                if (outcome == StripeConnectPayoutReconciliationService.Outcome.PENDING) {
                    queue.recordProviderHealthy(command.payoutId());
                }
            } catch (RuntimeException ex) {
                log.warn("Stripe Connect payout reconciliation failed for payout {}", command.payoutId(), ex);
                queue.recordProviderFailure(command.payoutId(), "STRIPE_RECONCILIATION_ERROR");
            }
        }
    }
}
