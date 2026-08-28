package com.doFast.dofastapp.payout.service;

import com.doFast.dofastapp.payout.provider.PayoutDispatchCommand;
import com.doFast.dofastapp.payout.provider.PayoutDispatchResult;
import com.doFast.dofastapp.payout.provider.PayoutProvider;
import com.doFast.dofastapp.payout.provider.PayoutProviderRegistry;
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
        queue.recoverOneStaleProcessing();
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
            } catch (RuntimeException ex) {
                log.warn("Payout provider call failed for payout {}", command.payoutId(), ex);
                result = PayoutDispatchResult.retryableFailure("PROVIDER_EXCEPTION");
            }
            queue.complete(command.payoutId(), result);
        }
    }
}
