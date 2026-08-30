package com.doFast.dofastapp.payment.risk;

import com.doFast.dofastapp.payment.risk.entity.StripePaymentDispute;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StripePaymentDisputeTest {

    @Test
    void partialRecoveryTracksOnlyUncoveredPlatformExposure() {
        StripePaymentDispute dispute = dispute("50.00");
        LocalDateTime now = LocalDateTime.now();

        dispute.markFundsWithdrawn(now);
        dispute.recordWalletRecovery(new BigDecimal("20.00"), now.plusSeconds(1));

        assertThat(dispute.getWalletRecoveredAmount()).isEqualByComparingTo("20.00");
        assertThat(dispute.getOutstandingAmount()).isEqualByComparingTo("30.00");
        assertThat(dispute.getRecoverySequence()).isEqualTo(1);
    }

    @Test
    void reinstatementReturnsOnlyMoneyActuallyRecoveredFromWallet() {
        StripePaymentDispute dispute = dispute("50.00");
        LocalDateTime now = LocalDateTime.now();

        dispute.markFundsWithdrawn(now);
        dispute.recordWalletRecovery(new BigDecimal("20.00"), now.plusSeconds(1));
        dispute.markFundsReinstated(dispute.amountToReturnToWallet(), now.plusSeconds(2));

        assertThat(dispute.getWalletRecoveredAmount()).isEqualByComparingTo("20.00");
        assertThat(dispute.getWalletReturnedAmount()).isEqualByComparingTo("20.00");
        assertThat(dispute.getOutstandingAmount()).isEqualByComparingTo("0.00");
        assertThat(dispute.isFundsReinstated()).isTrue();
    }

    @Test
    void outOfOrderReinstatementPreventsLaterWithdrawalFromCreatingDebt() {
        StripePaymentDispute dispute = dispute("50.00");
        LocalDateTime now = LocalDateTime.now();

        dispute.markFundsReinstated(BigDecimal.ZERO.setScale(2), now);
        dispute.markFundsWithdrawn(now.plusSeconds(1));

        assertThat(dispute.isFundsWithdrawn()).isTrue();
        assertThat(dispute.isFundsReinstated()).isTrue();
        assertThat(dispute.getOutstandingAmount()).isEqualByComparingTo("0.00");
        assertThat(dispute.getWalletRecoveredAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void olderStripeEventCannotRegressNewerDisputeState() {
        LocalDateTime stripeCreatedAt = LocalDateTime.of(2026, 8, 30, 20, 0);
        StripePaymentDispute dispute = dispute("50.00", stripeCreatedAt, "needs_response");

        boolean newerApplied = dispute.refreshFromStripeEvent(
                "ch_test",
                "general",
                "under_review",
                stripeCreatedAt.plusSeconds(20),
                stripeCreatedAt.plusMinutes(1)
        );
        boolean olderApplied = dispute.refreshFromStripeEvent(
                "ch_test",
                "fraudulent",
                "needs_response",
                stripeCreatedAt.plusSeconds(10),
                stripeCreatedAt.plusMinutes(2)
        );

        assertThat(newerApplied).isTrue();
        assertThat(olderApplied).isFalse();
        assertThat(dispute.getStripeStatus()).isEqualTo("under_review");
        assertThat(dispute.getReason()).isEqualTo("general");
        assertThat(dispute.getStripeStateEventCreatedAt()).isEqualTo(stripeCreatedAt.plusSeconds(20));
    }

    @Test
    void staleStripeEventStillCannotChangeImmutableChargeIdentity() {
        LocalDateTime stripeCreatedAt = LocalDateTime.of(2026, 8, 30, 20, 0);
        StripePaymentDispute dispute = dispute("50.00", stripeCreatedAt.plusSeconds(20), "under_review");

        assertThatThrownBy(() -> dispute.refreshFromStripeEvent(
                "ch_other",
                "fraudulent",
                "needs_response",
                stripeCreatedAt.plusSeconds(10),
                stripeCreatedAt.plusMinutes(1)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("changed charge identity");
    }

    @Test
    void equalTimestampCannotRegressTerminalStripeStatus() {
        LocalDateTime stripeCreatedAt = LocalDateTime.of(2026, 8, 30, 20, 0);
        StripePaymentDispute dispute = dispute("50.00", stripeCreatedAt, "won");

        boolean applied = dispute.refreshFromStripeEvent(
                "ch_test",
                "fraudulent",
                "under_review",
                stripeCreatedAt,
                stripeCreatedAt.plusMinutes(1)
        );

        assertThat(applied).isFalse();
        assertThat(dispute.getStripeStatus()).isEqualTo("won");
        assertThat(dispute.getStripeStateEventCreatedAt()).isEqualTo(stripeCreatedAt);
    }

    private StripePaymentDispute dispute(String amount) {
        return dispute(amount, null, "needs_response");
    }

    private StripePaymentDispute dispute(String amount, LocalDateTime stripeEventCreatedAt, String status) {
        StripePaymentDispute dispute = new StripePaymentDispute();
        dispute.initialize(
                "dp_test",
                "pi_test",
                "ch_test",
                7L,
                new BigDecimal(amount),
                "PLN",
                "fraudulent",
                status,
                LocalDateTime.now(),
                stripeEventCreatedAt
        );
        return dispute;
    }
}
