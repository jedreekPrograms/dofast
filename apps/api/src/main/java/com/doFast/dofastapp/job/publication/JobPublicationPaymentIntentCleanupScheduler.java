package com.doFast.dofastapp.job.publication;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class JobPublicationPaymentIntentCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(JobPublicationPaymentIntentCleanupScheduler.class);
    private static final int MAX_PER_TICK = 25;

    private final JobPublicationPaymentIntentCleanupService cleanupService;

    public JobPublicationPaymentIntentCleanupScheduler(JobPublicationPaymentIntentCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @Scheduled(fixedDelayString = "${dofast.job-publications.payment-intent-cleanup-interval-ms:30000}")
    public void cleanupCancelledPaymentIntents() {
        for (Long publicationId : cleanupService.findDueIds(MAX_PER_TICK)) {
            try {
                cleanupService.process(publicationId);
            } catch (RuntimeException ex) {
                // process(...) normally records provider failures itself. Keep one bad record from
                // aborting the remainder of this tick; an uncommitted/leased claim becomes due again.
                log.error("Unexpected cancelled publication PaymentIntent cleanup failure for {}", publicationId, ex);
            }
        }
    }
}
