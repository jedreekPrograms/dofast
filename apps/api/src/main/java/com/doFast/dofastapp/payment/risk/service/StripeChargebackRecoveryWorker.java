package com.doFast.dofastapp.payment.risk.service;

import com.doFast.dofastapp.payment.risk.repository.StripePaymentDisputeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class StripeChargebackRecoveryWorker {

    private static final Logger log = LoggerFactory.getLogger(StripeChargebackRecoveryWorker.class);
    private static final int BATCH_SIZE = 25;

    private final StripePaymentDisputeRepository disputeRepository;
    private final StripeChargebackRecoveryService recoveryService;

    public StripeChargebackRecoveryWorker(
            StripePaymentDisputeRepository disputeRepository,
            StripeChargebackRecoveryService recoveryService
    ) {
        this.disputeRepository = disputeRepository;
        this.recoveryService = recoveryService;
    }

    @Scheduled(fixedDelayString = "${dofast.payments.chargeback-recovery-interval-ms:15000}")
    public void recoverOutstandingExposure() {
        for (Long disputeId : disputeRepository.findRecoverableIds(BATCH_SIZE)) {
            try {
                recoveryService.recoverAvailableBalance(disputeId);
            } catch (RuntimeException ex) {
                log.error("Failed to recover wallet balance for Stripe dispute exposure {}", disputeId, ex);
            }
        }
    }
}
