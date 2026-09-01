package com.doFast.dofastapp.payout.provider;

/**
 * Signals that Stripe Connect returned a provider-side object after a money-movement call, but the
 * response violated doFast's expected payout contract. A provider side effect may already exist,
 * so the dispatch must be quarantined instead of being treated as a replayable transport failure.
 */
public class StripeConnectPayoutResponseException extends RuntimeException {

    private final String failureCode;
    private final String trustedTransferReference;
    private final String trustedPayoutReference;

    public StripeConnectPayoutResponseException(
            String message,
            String failureCode,
            String trustedTransferReference,
            String trustedPayoutReference
    ) {
        super(message);
        this.failureCode = failureCode;
        this.trustedTransferReference = trustedTransferReference;
        this.trustedPayoutReference = trustedPayoutReference;
    }

    public String failureCode() {
        return failureCode;
    }

    public String trustedTransferReference() {
        return trustedTransferReference;
    }

    public String trustedPayoutReference() {
        return trustedPayoutReference;
    }
}
