package com.doFast.dofastapp.job.publication;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class JobPublicationPaymentIntentCreateRecoveryScheduler {

    private static final Logger log = LoggerFactory.getLogger(JobPublicationPaymentIntentCreateRecoveryScheduler.class);
    private static final int MAX_PER_TICK = 25;

    private final JobPublicationPaymentIntentCreateRecoveryService recoveryService;

    public JobPublicationPaymentIntentCreateRecoveryScheduler(
            JobPublicationPaymentIntentCreateRecoveryService recoveryService
    ) {
        this.recoveryService = recoveryService;
    }

    @Scheduled(fixedDelayString = "${dofast.job-publications.payment-intent-create-recovery-interval-ms:30000}")
    public void recoverCancelledOrphanPaymentIntents() {
        for (Long publicationId : recoveryService.findDueIds(MAX_PER_TICK)) {
            try {
                recoveryService.process(publicationId);
            } catch (RuntimeException ex) {
                log.error("Unexpected publication PaymentIntent create-recovery failure for {}", publicationId, ex);
            }
        }
    }
}
