package com.doFast.dofastapp.payment.risk.service;

import com.doFast.dofastapp.common.exception.BusinessException;
import com.doFast.dofastapp.payment.risk.repository.StripePaymentDisputeRepository;
import com.doFast.dofastapp.wallet.enums.WalletTransactionType;
import com.doFast.dofastapp.wallet.service.WalletDebitGuard;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class ChargebackWalletDebitGuard implements WalletDebitGuard {

    private final StripePaymentDisputeRepository disputeRepository;

    public ChargebackWalletDebitGuard(StripePaymentDisputeRepository disputeRepository) {
        this.disputeRepository = disputeRepository;
    }

    @Override
    public void assertDebitAllowed(Long userId, BigDecimal amount, WalletTransactionType type) {
        if (type == WalletTransactionType.CHARGEBACK_RECOVERY) {
            return;
        }
        if (disputeRepository.existsByUserIdAndOutstandingAmountGreaterThan(userId, BigDecimal.ZERO)) {
            throw new BusinessException(
                    "Konto ma nierozliczony chargeback Stripe. Środki przychodzące zostaną najpierw użyte do pokrycia ekspozycji"
            );
        }
    }
}
