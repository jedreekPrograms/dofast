package com.doFast.dofastapp.payment.risk;

import com.doFast.dofastapp.payment.risk.entity.StripePaymentDispute;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

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

    private StripePaymentDispute dispute(String amount) {
        StripePaymentDispute dispute = new StripePaymentDispute();
        dispute.initialize(
                "dp_test",
                "pi_test",
                "ch_test",
                7L,
                new BigDecimal(amount),
                "PLN",
                "fraudulent",
                "needs_response",
                LocalDateTime.now()
        );
        return dispute;
    }
}
