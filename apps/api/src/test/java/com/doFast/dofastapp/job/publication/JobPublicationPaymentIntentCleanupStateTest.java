package com.doFast.dofastapp.job.publication;

import com.doFast.dofastapp.user.entity.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class JobPublicationPaymentIntentCleanupStateTest {

    @Test
    void cancellationPersistsDueCleanupAndClaimCreatesRestartLease() {
        JobPublication publication = paymentRequiredPublication();
        LocalDateTime cancelledAt = LocalDateTime.of(2026, 8, 31, 12, 0);
        publication.attachStripePaymentIntent("pi_cleanup", cancelledAt.minusMinutes(1));

        publication.cancel(cancelledAt);

        assertThat(publication.getStatus()).isEqualTo(JobPublicationStatus.CANCELLED);
        assertThat(publication.getStripePaymentIntentCleanupAttemptCount()).isZero();
        assertThat(publication.getStripePaymentIntentCleanupNextAttemptAt()).isEqualTo(cancelledAt);
        assertThat(publication.getStripePaymentIntentCleanupCompletedAt()).isNull();
        assertThat(publication.isStripePaymentIntentCleanupReviewRequired()).isFalse();

        LocalDateTime leaseUntil = cancelledAt.plusMinutes(2);
        assertThat(publication.claimStripePaymentIntentCleanup(cancelledAt, leaseUntil)).isTrue();
        assertThat(publication.getStripePaymentIntentCleanupAttemptCount()).isEqualTo(1);
        assertThat(publication.getStripePaymentIntentCleanupNextAttemptAt()).isEqualTo(leaseUntil);

        // A second worker that saw the old due-id snapshot must re-check the locked row and lose.
        assertThat(publication.claimStripePaymentIntentCleanup(cancelledAt.plusSeconds(1), leaseUntil.plusMinutes(2)))
                .isFalse();
    }

    @Test
    void providerSuccessAfterCrashCanCompleteRecoveredLease() {
        JobPublication publication = paymentRequiredPublication();
        LocalDateTime cancelledAt = LocalDateTime.of(2026, 8, 31, 12, 0);
        publication.attachStripePaymentIntent("pi_cleanup", cancelledAt.minusMinutes(1));
        publication.cancel(cancelledAt);
        publication.claimStripePaymentIntentCleanup(cancelledAt, cancelledAt.plusMinutes(2));

        // Simulates the retry after Stripe cancel succeeded but the process died before local completion.
        publication.completeStripePaymentIntentCleanup(cancelledAt.plusMinutes(3));

        assertThat(publication.getStripePaymentIntentCleanupCompletedAt()).isEqualTo(cancelledAt.plusMinutes(3));
        assertThat(publication.getStripePaymentIntentCleanupNextAttemptAt()).isNull();
        assertThat(publication.getStripePaymentIntentCleanupLastError()).isNull();
    }

    @Test
    void lateSuccessfulPaymentTerminatesCleanupWithoutResurrectingPublication() {
        JobPublication publication = paymentRequiredPublication();
        LocalDateTime cancelledAt = LocalDateTime.of(2026, 8, 31, 12, 0);
        publication.attachStripePaymentIntent("pi_cleanup", cancelledAt.minusMinutes(1));
        publication.cancel(cancelledAt);

        publication.markLatePaymentAfterCancellation(cancelledAt.plusSeconds(30));

        assertThat(publication.getStatus()).isEqualTo(JobPublicationStatus.CANCELLED);
        assertThat(publication.getRecoveryReason())
                .isEqualTo(JobPublicationRecoveryReason.CANCELLED_BEFORE_PAYMENT_CONFIRMED);
        assertThat(publication.getPaymentReceivedAt()).isEqualTo(cancelledAt.plusSeconds(30));
        assertThat(publication.getStripePaymentIntentCleanupCompletedAt()).isEqualTo(cancelledAt.plusSeconds(30));
        assertThat(publication.getStripePaymentIntentCleanupNextAttemptAt()).isNull();
    }

    private JobPublication paymentRequiredPublication() {
        JobPublication publication = new JobPublication();
        LocalDateTime now = LocalDateTime.of(2026, 8, 31, 11, 50);
        publication.initializePaymentRequired(
                mock(User.class),
                "job-publication:1:test",
                "payload-hash",
                "{}",
                1L,
                null,
                new BigDecimal("20.00"),
                new BigDecimal("5.00"),
                new BigDecimal("15.00"),
                now,
                now.plusMinutes(10)
        );
        return publication;
    }
}
