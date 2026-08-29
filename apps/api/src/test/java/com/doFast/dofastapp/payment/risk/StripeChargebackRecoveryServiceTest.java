package com.doFast.dofastapp.payment.risk;

import com.doFast.dofastapp.payment.risk.entity.StripePaymentDispute;
import com.doFast.dofastapp.payment.risk.repository.StripePaymentDisputeRepository;
import com.doFast.dofastapp.payment.risk.service.StripeChargebackRecoveryService;
import com.doFast.dofastapp.wallet.enums.WalletTransactionType;
import com.doFast.dofastapp.wallet.service.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StripeChargebackRecoveryServiceTest {

    @Test
    void recoversOnlyAvailableBalanceAndLeavesDurableOutstandingExposure() {
        StripePaymentDisputeRepository repository = mock(StripePaymentDisputeRepository.class);
        WalletService walletService = mock(WalletService.class);
        StripeChargebackRecoveryService service = new StripeChargebackRecoveryService(repository, walletService);

        StripePaymentDispute dispute = new StripePaymentDispute();
        dispute.initialize(
                "dp_partial",
                "pi_partial",
                "ch_partial",
                7L,
                new BigDecimal("50.00"),
                "PLN",
                "fraudulent",
                "needs_response",
                LocalDateTime.now()
        );
        dispute.markFundsWithdrawn(LocalDateTime.now());
        ReflectionTestUtils.setField(dispute, "id", 12L);

        when(repository.findById(12L)).thenReturn(Optional.of(dispute));
        when(repository.findByIdForUpdate(12L)).thenReturn(Optional.of(dispute));
        when(walletService.getBalanceForUpdate(7L)).thenReturn(new BigDecimal("20.00"));
        when(walletService.debit(
                7L,
                new BigDecimal("20.00"),
                WalletTransactionType.CHARGEBACK_RECOVERY,
                null,
                "stripe:dispute:dp_partial:recovery:0"
        )).thenReturn(true);

        BigDecimal recovered = service.recoverAvailableBalance(12L);

        assertThat(recovered).isEqualByComparingTo("20.00");
        assertThat(dispute.getWalletRecoveredAmount()).isEqualByComparingTo("20.00");
        assertThat(dispute.getOutstandingAmount()).isEqualByComparingTo("30.00");
        verify(walletService).debit(
                7L,
                new BigDecimal("20.00"),
                WalletTransactionType.CHARGEBACK_RECOVERY,
                null,
                "stripe:dispute:dp_partial:recovery:0"
        );
    }
}
