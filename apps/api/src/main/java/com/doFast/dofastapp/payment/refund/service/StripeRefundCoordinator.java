package com.doFast.dofastapp.payment.refund.service;

import com.doFast.dofastapp.payment.refund.dto.CreateStripeRefundRequest;
import com.doFast.dofastapp.payment.refund.dto.StripeRefundResponse;
import org.springframework.stereotype.Service;

@Service
public class StripeRefundCoordinator {

    private final StripeRefundRequestService requestService;
    private final StripeRefundDispatchService dispatchService;

    public StripeRefundCoordinator(
            StripeRefundRequestService requestService,
            StripeRefundDispatchService dispatchService
    ) {
        this.requestService = requestService;
        this.dispatchService = dispatchService;
    }

    public StripeRefundResponse request(Long userId, CreateStripeRefundRequest input) {
        StripeRefundResponse created = requestService.create(userId, input);
        dispatchService.dispatch(created.id());
        return requestService.get(created.id(), userId);
    }

    public StripeRefundResponse get(Long requestId, Long userId) {
        return requestService.get(requestId, userId);
    }
}
