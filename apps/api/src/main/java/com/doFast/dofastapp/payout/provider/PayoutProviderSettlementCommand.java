package com.doFast.dofastapp.payout.provider;

public record PayoutProviderSettlementCommand(
        String providerCode,
        String providerEventId,
        String providerReference,
        PayoutProviderSettlementOutcome outcome,
        String failureCode
) {}
