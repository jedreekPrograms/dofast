package com.doFast.dofastapp.job.publication;

import com.doFast.dofastapp.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class JobPublicationPaymentIntentCreateStateTest {

    @Test
    void durableCreateClaimRecordsStartAttemptAndLease() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 31, 12, 0);
        JobPublication publication = paymentRequiredPublication(now);

        assertThat(publication.claimStripePaymentIntentCreate(now, now.plusMinutes(2))).isTrue();
        assertThat(publication.getStripePaymentIntentCreateStartedAt()).isEqualTo(now);
        assertThat(publication.getStripePaymentIntentCreateAttemptCount()).isEqualTo(1);
        assertThat(publication.getStripePaymentIntentCreateNextAttemptAt()).isEqualTo(now.plusMinutes(2));

        assertThat(publication.claimStripePaymentIntentCreate(now.plusSeconds(10), now.plusMinutes(3))).isFalse();
        assertThat(publication.getStripePaymentIntentCreateAttemptCount()).isEqualTo(1);
    }

    @Test
    void cancellationDuringProviderCallPreservesCreateLeaseUntilProviderFinishes() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 31, 12, 0);
        JobPublication publication = paymentRequiredPublication(now);
        LocalDateTime leaseUntil = now.plusMinutes(2);

        assertThat(publication.claimStripePaymentIntentCreate(now, leaseUntil)).isTrue();
        publication.cancel(now.plusSeconds(5));

        assertThat(publication.getStatus()).isEqualTo(JobPublicationStatus.CANCELLED);
        assertThat(publication.getStripePaymentIntentCreateNextAttemptAt()).isEqualTo(leaseUntil);
        assertThat(publication.getStripePaymentIntentCleanupNextAttemptAt()).isNull();

        publication.attachStripePaymentIntent("pi_recovered", now.plusSeconds(20));

        assertThat(publication.getStripePaymentIntentId()).isEqualTo("pi_recovered");
        assertThat(publication.getStripePaymentIntentCreateNextAttemptAt()).isNull();
        assertThat(publication.getStripePaymentIntentCleanupNextAttemptAt()).isEqualTo(now.plusSeconds(20));
        assertThat(publication.isStripePaymentIntentCreateReviewRequired()).isFalse();
    }

    @Test
    void crashedCreateBecomesRecoverableAfterLeaseExpiresWithoutLosingOriginalStartTime() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 31, 12, 0);
        JobPublication publication = paymentRequiredPublication(now);

        assertThat(publication.claimStripePaymentIntentCreate(now, now.plusMinutes(2))).isTrue();
        publication.cancel(now.plusSeconds(10));
        publication.retryStripePaymentIntentCreate("STRIPE_EXCEPTION", now.plusMinutes(3), now.plusMinutes(2));

        assertThat(publication.getStripePaymentIntentCreateStartedAt()).isEqualTo(now);
        assertThat(publication.getStripePaymentIntentCreateNextAttemptAt()).isEqualTo(now.plusMinutes(3));
        assertThat(publication.getStripePaymentIntentCreateLastError()).isEqualTo("STRIPE_EXCEPTION");
    }

    private JobPublication paymentRequiredPublication(LocalDateTime now) {
        User owner = new User("owner@example.com", "owner");
        ReflectionTestUtils.setField(owner, "id", 7L);

        JobPublication publication = new JobPublication();
        publication.initializePaymentRequired(
                owner,
                "job-publication:7:req-create-state",
                "hash",
                "private-payload",
                42L,
                null,
                new BigDecimal("70.00"),
                new BigDecimal("25.00"),
                new BigDecimal("45.00"),
                now,
                now.plusMinutes(10)
        );
        ReflectionTestUtils.setField(publication, "id", 99L);
        return publication;
    }
}
