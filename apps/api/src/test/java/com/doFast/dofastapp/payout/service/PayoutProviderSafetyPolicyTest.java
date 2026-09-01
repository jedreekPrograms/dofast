package com.doFast.dofastapp.payout.service;

import com.doFast.dofastapp.payout.entity.PayoutRequest;
import com.doFast.dofastapp.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayoutProviderSafetyPolicyTest {

    @Test
    void retryWindowDoesNotResetWhenProcessingTimestampMovesForward() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 1, 4, 0);
        PayoutRequest payout = stripePayout(now.minusHours(24));

        payout.startProcessing(now.minusMinutes(5));

        assertTrue(PayoutProviderSafetyPolicy.stripeConnectRetryWindowExpired(payout, now));
    }

    @Test
    void recentPayoutRemainsInsideConservativeRetryWindow() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 1, 4, 0);
        PayoutRequest payout = stripePayout(now.minusHours(2));

        payout.startProcessing(now.minusMinutes(10));

        assertFalse(PayoutProviderSafetyPolicy.stripeConnectRetryWindowExpired(payout, now));
    }

    private PayoutRequest stripePayout(LocalDateTime requestedAt) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", 7L);

        PayoutRequest payout = new PayoutRequest();
        payout.initialize(
                user,
                "payout:7:client:clock-test",
                new BigDecimal("25.00"),
                "PLN",
                StripeConnectOnboardingService.PROVIDER_CODE,
                requestedAt
        );
        ReflectionTestUtils.setField(payout, "id", 41L);
        return payout;
    }
}
