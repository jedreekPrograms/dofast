package com.doFast.dofastapp.payout.service;

import com.doFast.dofastapp.payout.provider.PayoutDispatchCommand;
import com.doFast.dofastapp.payout.provider.PayoutDispatchResult;
import com.doFast.dofastapp.payout.provider.PayoutProvider;
import com.doFast.dofastapp.payout.provider.PayoutProviderRegistry;
import com.doFast.dofastapp.payout.provider.StripeConnectPayoutResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PayoutDispatcher {

    private static final Logger log = LoggerFactory.getLogger(PayoutDispatcher.class);
    private static final int MAX_PER_TICK = 10;

    private final PayoutProviderRegistry providerRegistry;
    private final PayoutDispatchQueue queue;

    public PayoutDispatcher(PayoutProviderRegistry providerRegistry, PayoutDispatchQueue queue) {
        this.providerRegistry = providerRegistry;
        this.queue = queue;
    }

    @Scheduled(fixedDelayString = "${dofast.payouts.dispatch-interval-ms:2000}")
    public void dispatch() {
        try {
            queue.recoverOneStaleProcessing();
        } catch (RuntimeException ex) {
            log.warn(
                    "Payout stale-processing recovery failed; leaving durable states unchanged and continuing with new dispatches",
                    ex
            );
        }

        if (!providerRegistry.isConfiguredProviderAvailable()) return;

        String providerCode = providerRegistry.configuredProviderCode();
        PayoutProvider provider = providerRegistry.requireProvider(providerCode);
        for (int index = 0; index < MAX_PER_TICK; index++) {
            var claim = queue.claimNext(providerCode);
            if (claim.isEmpty()) return;
            PayoutDispatchCommand command = claim.get();
            PayoutDispatchResult result;
            try {
                result = provider.dispatch(command);
            } catch (StripeConnectPayoutResponseException ex) {
                log.error(
                        "Stripe Connect returned an anomalous response after payout provider activity for payout {}; automatic retry is blocked with code {}",
                        command.payoutId(),
                        ex.failureCode()
                );
                try {
                    queue.quarantineProviderResponse(
                            command.payoutId(),
                            ex.trustedTransferReference(),
                            ex.trustedPayoutReference(),
                            ex.failureCode()
                    );
                } catch (RuntimeException persistenceFailure) {
                    log.error(
                            "Failed to persist Stripe Connect response quarantine for payout {}; leaving PROCESSING for bounded stale recovery",
                            command.payoutId(),
                            persistenceFailure
                    );
                }
                continue;
            } catch (RuntimeException ex) {
                log.warn("Payout provider call failed for payout {}", command.payoutId(), ex);
                result = PayoutDispatchResult.retryableFailure("PROVIDER_EXCEPTION");
            }
            try {
                queue.complete(command.payoutId(), result);
            } catch (RuntimeException ex) {
                log.warn(
                        "Failed to persist payout provider result for payout {}; leaving PROCESSING state for durable stale recovery and continuing the batch",
                        command.payoutId(),
                        ex
                );
            }
        }
    }
}
