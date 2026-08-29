package com.doFast.dofastapp.payout.provider;

public record PayoutDispatchResult(
        boolean successful,
        boolean retryable,
        boolean settlementPending,
        String providerReference,
        String failureCode
) {
    public PayoutDispatchResult {
        if (successful) {
            if (retryable || providerReference == null || providerReference.isBlank() || failureCode != null) {
                throw new IllegalArgumentException("Successful payout dispatch requires a provider reference and no failure");
            }
        } else if (settlementPending || providerReference != null) {
            throw new IllegalArgumentException("Failed payout dispatch cannot carry a pending settlement or provider reference");
        }
        if (settlementPending && !successful) {
            throw new IllegalArgumentException("Only successful dispatch can await settlement");
        }
    }

    public static PayoutDispatchResult success(String providerReference) {
        return new PayoutDispatchResult(true, false, false, providerReference, null);
    }

    public static PayoutDispatchResult submitted(String providerReference) {
        return new PayoutDispatchResult(true, false, true, providerReference, null);
    }

    public static PayoutDispatchResult retryableFailure(String failureCode) {
        return new PayoutDispatchResult(false, true, false, null, failureCode);
    }

    public static PayoutDispatchResult definitiveFailure(String failureCode) {
        return new PayoutDispatchResult(false, false, false, null, failureCode);
    }
}
