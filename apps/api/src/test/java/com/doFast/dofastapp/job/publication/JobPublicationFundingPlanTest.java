package com.doFast.dofastapp.job.publication;

import com.doFast.dofastapp.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobPublicationFundingPlanTest {

    @Test
    void reservesLessWalletWhenShortfallIsBelowStripeMinimum() {
        JobPublicationService.FundingPlan plan = JobPublicationService.fundingPlan(
                new BigDecimal("25.50"),
                new BigDecimal("25.00")
        );

        assertThat(plan.walletReservedAmount()).isEqualByComparingTo("24.50");
        assertThat(plan.onlinePaymentAmount()).isEqualByComparingTo("1.00");
        assertThat(plan.walletReservedAmount().add(plan.onlinePaymentAmount()))
                .isEqualByComparingTo("25.50");
    }

    @Test
    void ordinaryShortfallStillChargesExactlyWhatIsMissing() {
        JobPublicationService.FundingPlan plan = JobPublicationService.fundingPlan(
                new BigDecimal("70.00"),
                new BigDecimal("25.00")
        );

        assertThat(plan.walletReservedAmount()).isEqualByComparingTo("25.00");
        assertThat(plan.onlinePaymentAmount()).isEqualByComparingTo("45.00");
    }

    @Test
    void negativeWalletBalanceIsNeverReserved() {
        JobPublicationService.FundingPlan plan = JobPublicationService.fundingPlan(
                new BigDecimal("70.00"),
                new BigDecimal("-5.00")
        );

        assertThat(plan.walletReservedAmount()).isEqualByComparingTo("0.00");
        assertThat(plan.onlinePaymentAmount()).isEqualByComparingTo("70.00");
    }

    @Test
    void subMinimumJobCannotUsePublicationPaymentWithoutOvercharging() {
        assertThatThrownBy(() -> JobPublicationService.fundingPlan(
                new BigDecimal("0.50"),
                BigDecimal.ZERO
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("minimalna płatność online 1,00 PLN");
    }
}
