package com.doFast.dofastapp.payment.refund.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class StripeRefundScheduler {

    private static final Logger log = LoggerFactory.getLogger(StripeRefundScheduler.class);
    private static final int BATCH_SIZE = 10;

    private final StripeRefundRequestService requestService;
    private final StripeRefundDispatchService dispatchService;

    public StripeRefundScheduler(
            StripeRefundRequestService requestService,
            StripeRefundDispatchService dispatchService
    ) {
        this.requestService = requestService;
        this.dispatchService = dispatchService;
    }

    @Scheduled(fixedDelayString = "${stripe.refunds.dispatch-delay-ms:5000}")
    public void dispatchPendingRefunds() {
        requestService.requeueStaleDispatches();
        for (Long id : requestService.findDispatchableIds(BATCH_SIZE)) {
            try {
                dispatchService.dispatch(id);
            } catch (RuntimeException ex) {
                log.warn(
                        "Unexpected refund dispatch failure for request {}; leaving its durable state for stale recovery and continuing the batch",
                        id,
                        ex
                );
            }
        }
    }
}
