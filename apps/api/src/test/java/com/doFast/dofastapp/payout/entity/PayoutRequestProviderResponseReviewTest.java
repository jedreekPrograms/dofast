package com.doFast.dofastapp.payout.entity;

import com.doFast.dofastapp.payout.enums.PayoutStatus;
import com.doFast.dofastapp.user.entity.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayoutRequestProviderResponseReviewTest {

    @Test
    void trustedProviderReferencesArePreservedWithoutResolvingFunds() {
        PayoutRequest payout = processingPayout();

        boolean changed = payout.recordProviderResponseForReview(
                "tr_41",
                "po_41",
                "STRIPE_PAYOUT_AMOUNT_MISMATCH",
                LocalDateTime.of(2026, 8, 31, 20, 1)
        );

        assertTrue(changed);
        assertEquals(PayoutStatus.REVIEW_REQUIRED, payout.getStatus());
        assertEquals("tr_41", payout.getProviderTransferReference());
        assertEquals("po_41", payout.getProviderReference());
        assertEquals("STRIPE_PAYOUT_AMOUNT_MISMATCH", payout.getFailureCode());
        assertNull(payout.getResolvedAt());
    }

    @Test
    void untrustedProviderReferencesAreNotInvented() {
        PayoutRequest payout = processingPayout();

        boolean changed = payout.recordProviderResponseForReview(
                null,
                null,
                "STRIPE_TRANSFER_IDENTITY_MISMATCH",
                LocalDateTime.of(2026, 8, 31, 20, 1)
        );

        assertTrue(changed);
        assertEquals(PayoutStatus.REVIEW_REQUIRED, payout.getStatus());
        assertNull(payout.getProviderTransferReference());
        assertNull(payout.getProviderReference());
    }

    @Test
    void lateSyncResponseCannotRegressWebhookResolvedState() {
        PayoutRequest payout = processingPayout();
        payout.markSubmitted("po_41", LocalDateTime.of(2026, 8, 31, 20, 1));
        payout.markSubmittedPaid(LocalDateTime.of(2026, 8, 31, 20, 2));

        boolean changed = payout.recordProviderResponseForReview(
                "tr_other",
                "po_other",
                "STRIPE_PAYOUT_AMOUNT_MISMATCH",
                LocalDateTime.of(2026, 8, 31, 20, 3)
        );

        assertFalse(changed);
        assertEquals(PayoutStatus.PAID, payout.getStatus());
        assertEquals("po_41", payout.getProviderReference());
        assertNull(payout.getProviderTransferReference());
    }

    private PayoutRequest processingPayout() {
        PayoutRequest payout = new PayoutRequest();
        payout.initialize(
                new User(),
                "payout-request-41",
                new BigDecimal("25.00"),
                "PLN",
                "stripe-connect",
                LocalDateTime.of(2026, 8, 31, 20, 0)
        );
        payout.startProcessing(LocalDateTime.of(2026, 8, 31, 20, 0, 1));
        return payout;
    }
}
