package com.doFast.dofastapp.payout.service;

import com.doFast.dofastapp.payout.entity.PayoutRequest;
import com.doFast.dofastapp.payout.enums.PayoutStatus;

import java.time.Duration;
import java.time.LocalDateTime;

final class PayoutProviderSafetyPolicy {

    static final String STRIPE_CONNECT_IDEMPOTENCY_WINDOW_EXPIRED = "STRIPE_IDEMPOTENCY_WINDOW_EXPIRED";
    private static final Duration STRIPE_CONNECT_SAFE_RETRY_WINDOW = Duration.ofHours(23);

    private PayoutProviderSafetyPolicy() {}

    static boolean stripeConnectRetryWindowExpired(PayoutRequest payout, LocalDateTime now) {
        if (payout == null || now == null
                || !StripeConnectOnboardingService.PROVIDER_CODE.equals(payout.getProviderCode())) {
            return false;
        }

        // Stripe idempotency keys are only guaranteed for a bounded provider-side retention window.
        // processingStartedAt is intentionally reset on every retry, so using it here would let a
        // sequence of retries continuously move the safety deadline forward. requestedAt never moves;
        // using it as the lower-bound clock is conservative (it can quarantine an old queued payout
        // earlier than strictly necessary) but it can never authorize a retry after our safe window.
        LocalDateTime durableRetryOrigin = payout.getRequestedAt();
        return durableRetryOrigin == null
                || !durableRetryOrigin.isAfter(now.minus(STRIPE_CONNECT_SAFE_RETRY_WINDOW));
    }

    static boolean requiresExternalProviderReconciliation(PayoutRequest payout) {
        return payout != null
                && payout.getStatus() == PayoutStatus.REVIEW_REQUIRED
                && StripeConnectOnboardingService.PROVIDER_CODE.equals(payout.getProviderCode())
                && payout.getProviderReference() == null
                && payout.getAttemptCount() > 0;
    }
}
