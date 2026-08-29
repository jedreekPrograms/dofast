package com.doFast.dofastapp.payment.risk;

import com.doFast.dofastapp.common.exception.BusinessException;
import com.doFast.dofastapp.payment.risk.repository.StripePaymentDisputeRepository;
import com.doFast.dofastapp.payment.risk.service.ChargebackWalletDebitGuard;
import com.doFast.dofastapp.wallet.enums.WalletTransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChargebackWalletDebitGuardTest {

    @Test
    void outstandingExposureBlocksOrdinaryOutgoingMoney() {
        StripePaymentDisputeRepository repository = mock(StripePaymentDisputeRepository.class);
        when(repository.existsByUserIdAndOutstandingAmountGreaterThan(7L, BigDecimal.ZERO)).thenReturn(true);
        ChargebackWalletDebitGuard guard = new ChargebackWalletDebitGuard(repository);

        assertThatThrownBy(() -> guard.assertDebitAllowed(
                7L,
                new BigDecimal("10.00"),
                WalletTransactionType.PAYOUT_RESERVE
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("chargeback");
    }

    @Test
    void recoveryDebitBypassesTheGuardSoExposureCanBeCollected() {
        StripePaymentDisputeRepository repository = mock(StripePaymentDisputeRepository.class);
        ChargebackWalletDebitGuard guard = new ChargebackWalletDebitGuard(repository);

        assertThatCode(() -> guard.assertDebitAllowed(
                7L,
                new BigDecimal("10.00"),
                WalletTransactionType.CHARGEBACK_RECOVERY
        )).doesNotThrowAnyException();

        verify(repository, never()).existsByUserIdAndOutstandingAmountGreaterThan(7L, BigDecimal.ZERO);
    }
}
