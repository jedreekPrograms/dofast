package com.doFast.dofastapp.config;

import com.stripe.Stripe;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StripeConfigTest {

    @Test
    void initVerifiesReviewedSdkVersionBeforeConfiguringApiKey() {
        String previousApiKey = Stripe.apiKey;
        try {
            StripeConfig config = new StripeConfig("sk_test_version_contract");

            config.init();

            assertEquals("sk_test_version_contract", Stripe.apiKey);
        } finally {
            Stripe.apiKey = previousApiKey;
        }
    }
}
