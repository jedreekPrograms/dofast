package com.doFast.dofastapp.payment.refund.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StripeRefundRequestProviderResponseReviewTest {

    @Test
    void knownProviderResponseAnomalyPreservesIdentityAndReserveForReview() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 31, 19, 0);
        StripeRefundRequest request = request(now);
        request.startDispatch(now.plusSeconds(1));

        boolean recorded = request.recordProviderResponseForReview(
                "re_41",
                "succeeded",
                "provider_amount_mismatch",
                now.plusSeconds(2)
        );

        assertTrue(recorded);
        assertEquals(StripeRefundStatus.REVIEW_REQUIRED, request.getStatus());
        assertEquals("re_41", request.getStripeRefundId());
        assertEquals("succeeded", request.getStripeStatus());
        assertEquals("provider_amount_mismatch", request.getFailureReason());
        assertFalse(request.isWalletRestored());
        assertNull(request.getNextAttemptAt());
        assertNull(request.getResolvedAt());
    }

    @Test
    void laterVerifiedProviderEventCanResolveQuarantinedRequest() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 31, 19, 0);
        StripeRefundRequest request = request(now);
        request.startDispatch(now.plusSeconds(1));
        request.recordProviderResponseForReview(
                "re_41",
                "pending",
                "provider_amount_mismatch",
                now.plusSeconds(2)
        );

        boolean applied = request.applyProviderEvent(
                "re_41",
                "succeeded",
                null,
                1_788_194_000L,
                now.plusSeconds(3)
        );

        assertTrue(applied);
        assertEquals(StripeRefundStatus.SUCCEEDED, request.getStatus());
        assertEquals("re_41", request.getStripeRefundId());
        assertFalse(request.isWalletRestored());
    }

    @Test
    void synchronousResponseDoesNotOverrideWebhookThatSettledFirst() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 31, 19, 0);
        StripeRefundRequest request = request(now);
        request.startDispatch(now.plusSeconds(1));
        request.applyProviderEvent(
                "re_41",
                "succeeded",
                null,
                1_788_194_000L,
                now.plusSeconds(2)
        );

        boolean recorded = request.recordProviderResponseForReview(
                "re_41",
                "pending",
                "provider_amount_mismatch",
                now.plusSeconds(3)
        );

        assertFalse(recorded);
        assertEquals(StripeRefundStatus.SUCCEEDED, request.getStatus());
        assertEquals("re_41", request.getStripeRefundId());
        assertNull(request.getFailureReason());
    }

    private StripeRefundRequest request(LocalDateTime now) {
        return StripeRefundRequest.create(
                7L,
                "pi_41",
                "request-41",
                new BigDecimal("25.00"),
                "PLN",
                now
        );
    }
}
