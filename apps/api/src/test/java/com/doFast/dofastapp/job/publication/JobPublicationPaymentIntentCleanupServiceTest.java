package com.doFast.dofastapp.job.publication;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JobPublicationPaymentIntentCleanupServiceTest {

    @Test
    void cancelsOnlyStripeStatesThatCanStillBeAbandoned() {
        assertThat(JobPublicationPaymentIntentCleanupService.isCancellableStatus("requires_payment_method")).isTrue();
        assertThat(JobPublicationPaymentIntentCleanupService.isCancellableStatus("requires_confirmation")).isTrue();
        assertThat(JobPublicationPaymentIntentCleanupService.isCancellableStatus("requires_action")).isTrue();
        assertThat(JobPublicationPaymentIntentCleanupService.isCancellableStatus("requires_capture")).isTrue();
        assertThat(JobPublicationPaymentIntentCleanupService.isCancellableStatus("processing")).isTrue();

        assertThat(JobPublicationPaymentIntentCleanupService.isCancellableStatus("succeeded")).isFalse();
        assertThat(JobPublicationPaymentIntentCleanupService.isCancellableStatus("canceled")).isFalse();
        assertThat(JobPublicationPaymentIntentCleanupService.isCancellableStatus(null)).isFalse();
    }
}
