package com.doFast.dofastapp.payment.refund.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class StripeRefundScheduler {

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
            dispatchService.dispatch(id);
        }
    }
}
