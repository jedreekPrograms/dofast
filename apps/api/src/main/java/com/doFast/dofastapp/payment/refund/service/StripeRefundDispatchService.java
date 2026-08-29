package com.doFast.dofastapp.payment.refund.service;

import com.doFast.dofastapp.payment.exception.PaymentProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class StripeRefundDispatchService {

    private static final Logger log = LoggerFactory.getLogger(StripeRefundDispatchService.class);

    private final StripeRefundRequestService requestService;
    private final StripeRefundGateway gateway;

    public StripeRefundDispatchService(StripeRefundRequestService requestService, StripeRefundGateway gateway) {
        this.requestService = requestService;
        this.gateway = gateway;
    }

    public void dispatch(Long requestId) {
        StripeRefundDispatchCommand command = requestService.claimForDispatch(requestId);
        if (command == null) {
            return;
        }
        try {
            StripeRefundProviderResult result = gateway.create(command);
            requestService.recordProviderResult(requestId, result);
        } catch (PaymentProviderException ex) {
            log.warn("Stripe refund dispatch failed for request {} attempt {}", requestId, command.attempt());
            requestService.recordDispatchFailure(requestId);
        }
    }
}
