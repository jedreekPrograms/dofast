package com.doFast.dofastapp.payment.risk.service;

import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.payment.risk.entity.StripePaymentDispute;
import com.doFast.dofastapp.payment.risk.repository.StripePaymentDisputeRepository;
import com.doFast.dofastapp.wallet.enums.WalletTransactionType;
import com.doFast.dofastapp.wallet.service.WalletService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class StripeChargebackRecoveryService {

    private final StripePaymentDisputeRepository disputeRepository;
    private final WalletService walletService;

    public StripeChargebackRecoveryService(
            StripePaymentDisputeRepository disputeRepository,
            WalletService walletService
    ) {
        this.disputeRepository = disputeRepository;
        this.walletService = walletService;
    }

    @Transactional
    public BigDecimal recoverAvailableBalance(Long disputeId) {
        StripePaymentDispute snapshot = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new IllegalStateException("Stripe dispute exposure does not exist"));

        // Lock order is deliberately wallet -> dispute. Ordinary wallet debits also lock the wallet
        // before consulting the dispute guard, preventing an inverse lock order with the recovery worker.
        BigDecimal walletBalance = walletService.getBalanceForUpdate(snapshot.getUserId());
        StripePaymentDispute dispute = disputeRepository.findByIdForUpdate(disputeId)
                .orElseThrow(() -> new IllegalStateException("Stripe dispute exposure disappeared"));

        if (!dispute.getUserId().equals(snapshot.getUserId())) {
            throw new ConflictException("Stripe dispute changed user identity");
        }
        if (!dispute.isFundsWithdrawn() || dispute.isFundsReinstated()
                || dispute.getOutstandingAmount().signum() <= 0 || walletBalance.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }

        BigDecimal recoveryAmount = walletBalance.min(dispute.getOutstandingAmount()).setScale(2);
        String operationKey = "stripe:dispute:" + dispute.getStripeDisputeId()
                + ":recovery:" + dispute.getRecoverySequence();

        boolean debited = walletService.debit(
                dispute.getUserId(),
                recoveryAmount,
                WalletTransactionType.CHARGEBACK_RECOVERY,
                null,
                operationKey
        );
        if (!debited) {
            throw new ConflictException("Chargeback recovery ledger exists without matching dispute state");
        }

        dispute.recordWalletRecovery(recoveryAmount, LocalDateTime.now());
        return recoveryAmount;
    }
}
