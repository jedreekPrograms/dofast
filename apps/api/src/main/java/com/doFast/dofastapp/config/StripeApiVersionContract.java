package com.doFast.dofastapp.config;

import com.stripe.Stripe;

public final class StripeApiVersionContract {

    /**
     * Reviewed Stripe API schema expected by the payment integration.
     *
     * <p>stripe-java pins its generated model classes and outgoing requests to a concrete Stripe
     * API version. Changing the SDK can therefore change payment semantics even when Java code
     * still compiles. Update this value only after reviewing the Stripe API changelog and running
     * the financial contract test suite against the new SDK.
     */
    public static final String REVIEWED_API_VERSION = "2026-07-29.dahlia";

    private StripeApiVersionContract() {
    }

    public static void verifySdkVersion() {
        requireReviewedVersion("Stripe Java SDK", Stripe.API_VERSION);
    }

    public static boolean matchesReviewedVersion(String actualApiVersion) {
        return REVIEWED_API_VERSION.equals(actualApiVersion);
    }

    public static void requireReviewedVersion(String source, String actualApiVersion) {
        if (matchesReviewedVersion(actualApiVersion)) {
            return;
        }

        throw new IllegalStateException(
                source + " API version mismatch: reviewed=" + REVIEWED_API_VERSION
                        + ", actual=" + String.valueOf(actualApiVersion)
                        + ". Review the Stripe API changelog and payment/webhook contract before updating the reviewed version."
        );
    }
}
