package com.doFast.dofastapp.payout.provider;

import java.math.BigDecimal;

public record PayoutSubmittedReconciliationCommand(
        Long payoutId,
        Long userId,
        BigDecimal amount,
        String currency,
        String providerCode,
        String providerReference,
        String providerTransferReference
) {
    public PayoutSubmittedReconciliationCommand {
        if (payoutId == null || userId == null || amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Submitted payout reconciliation identity and amount are required");
        }
        if (currency == null || currency.isBlank()
                || providerCode == null || providerCode.isBlank()
                || providerReference == null || providerReference.isBlank()
                || providerTransferReference == null || providerTransferReference.isBlank()) {
            throw new IllegalArgumentException("Submitted payout reconciliation provider identity is required");
        }
    }
}
