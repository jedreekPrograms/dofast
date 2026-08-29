package com.doFast.dofastapp.payment.refund.service;

import com.doFast.dofastapp.payment.exception.PaymentProviderException;
import com.stripe.exception.StripeException;
import com.stripe.model.Refund;
import com.stripe.net.RequestOptions;
import com.stripe.param.RefundCreateParams;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class StripeRefundGateway {

    public StripeRefundProviderResult create(StripeRefundDispatchCommand command) {
        RefundCreateParams params = RefundCreateParams.builder()
                .setPaymentIntent(command.paymentIntentId())
                .setAmount(command.amount().movePointRight(2).longValueExact())
                .setReason(RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER)
                .putMetadata("dofastRefundId", command.requestId().toString())
                .putMetadata("userId", command.userId().toString())
                .build();
        RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey("dofast:refund:" + command.requestId())
                .build();

        try {
            Refund refund = Refund.create(params, options);
            validate(refund, command);
            return new StripeRefundProviderResult(refund.getId(), refund.getStatus(), refund.getFailureReason());
        } catch (StripeException ex) {
            throw new PaymentProviderException("Stripe refund request failed", ex);
        }
    }

    private void validate(Refund refund, StripeRefundDispatchCommand command) {
        if (refund == null || refund.getId() == null || refund.getId().isBlank()) {
            throw new PaymentProviderException("Stripe returned a refund without an id", null);
        }
        if (refund.getPaymentIntent() == null || !command.paymentIntentId().equals(refund.getPaymentIntent())) {
            throw new PaymentProviderException("Stripe refund does not match the requested PaymentIntent", null);
        }
        if (refund.getAmount() == null
                || BigDecimal.valueOf(refund.getAmount(), 2).compareTo(command.amount()) != 0) {
            throw new PaymentProviderException("Stripe refund does not match the requested amount", null);
        }
        if (refund.getCurrency() == null || !command.currency().equalsIgnoreCase(refund.getCurrency())) {
            throw new PaymentProviderException("Stripe refund does not match the requested currency", null);
        }
        if (refund.getStatus() == null || refund.getStatus().isBlank()) {
            throw new PaymentProviderException("Stripe returned a refund without a status", null);
        }
    }
}
