package com.doFast.dofastapp.job.publication;

public record JobPublicationPaymentIntentCleanupCommand(
        Long publicationId,
        String paymentIntentId,
        int attemptCount
) {}
