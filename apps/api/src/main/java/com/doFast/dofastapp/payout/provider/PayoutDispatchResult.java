package com.doFast.dofastapp.payout.provider;

public record PayoutDispatchResult(
        boolean successful,
        boolean retryable,
        String providerReference,
        String failureCode
) {
    public static PayoutDispatchResult success(String providerReference) {
        return new PayoutDispatchResult(true, false, providerReference, null);
    }

    public static PayoutDispatchResult retryableFailure(String failureCode) {
        return new PayoutDispatchResult(false, true, null, failureCode);
    }

    public static PayoutDispatchResult definitiveFailure(String failureCode) {
        return new PayoutDispatchResult(false, false, null, failureCode);
    }
}
