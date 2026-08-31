package com.doFast.dofastapp.payment.refund.service;

/**
 * Signals that Stripe returned a refund with a stable provider id, but the response violated the
 * business contract expected for the request. The provider-side side effect may already exist, so
 * this must not be handled as a transport failure or automatically retried.
 */
public class StripeRefundProviderResponseException extends RuntimeException {

    private final StripeRefundProviderResult providerResult;
    private final String violationCode;

    public StripeRefundProviderResponseException(
            String message,
            StripeRefundProviderResult providerResult,
            String violationCode
    ) {
        super(message);
        this.providerResult = providerResult;
        this.violationCode = violationCode;
    }

    public StripeRefundProviderResult providerResult() {
        return providerResult;
    }

    public String violationCode() {
        return violationCode;
    }
}
