package com.doFast.dofastapp.job.publication;

import java.math.BigDecimal;

public record JobPublicationPaymentIntentCreateCommand(
        Long publicationId,
        Long userId,
        BigDecimal amount,
        String currency,
        String idempotencyKey,
        String existingPaymentIntentId,
        int attemptCount
) {
    public boolean hasExistingPaymentIntent() {
        return existingPaymentIntentId != null && !existingPaymentIntentId.isBlank();
    }
}
