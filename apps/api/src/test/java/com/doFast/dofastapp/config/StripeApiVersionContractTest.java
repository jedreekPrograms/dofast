package com.doFast.dofastapp.config;

import com.stripe.Stripe;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StripeApiVersionContractTest {

    @Test
    void currentStripeSdkMatchesReviewedApiVersion() {
        assertEquals("2026-07-29.dahlia", StripeApiVersionContract.REVIEWED_API_VERSION);
        assertEquals(StripeApiVersionContract.REVIEWED_API_VERSION, Stripe.API_VERSION);
        assertDoesNotThrow(StripeApiVersionContract::verifySdkVersion);
    }

    @Test
    void mismatchedApiVersionFailsClosed() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> StripeApiVersionContract.requireReviewedVersion(
                        "Stripe Java SDK",
                        "2026-08-26.dahlia"
                )
        );

        assertTrue(exception.getMessage().contains(StripeApiVersionContract.REVIEWED_API_VERSION));
        assertTrue(exception.getMessage().contains("2026-08-26.dahlia"));
    }

    @Test
    void nullWebhookVersionDoesNotMatchReviewedContract() {
        assertFalse(StripeApiVersionContract.matchesReviewedVersion(null));
    }
}
