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
            if (refund == null || refund.getId() == null || refund.getId().isBlank()) {
                // No stable provider identity is available. Keep this in the existing ambiguous
                // retry path, which is bounded by the Stripe idempotency-key safety window.
                throw new PaymentProviderException("Stripe returned a refund without an id", null);
            }

            StripeRefundProviderResult result = new StripeRefundProviderResult(
                    refund.getId(),
                    refund.getStatus(),
                    refund.getFailureReason()
            );
            validateKnownProviderResponse(refund, command, result);
            return result;
        } catch (StripeException ex) {
            throw new PaymentProviderException("Stripe refund request failed", ex);
        }
    }

    void validateKnownProviderResponse(
            Refund refund,
            StripeRefundDispatchCommand command,
            StripeRefundProviderResult result
    ) {
        if (refund.getPaymentIntent() == null || !command.paymentIntentId().equals(refund.getPaymentIntent())) {
            throw responseMismatch(
                    "Stripe refund does not match the requested PaymentIntent",
                    result,
                    "provider_payment_intent_mismatch"
            );
        }
        if (refund.getAmount() == null
                || BigDecimal.valueOf(refund.getAmount(), 2).compareTo(command.amount()) != 0) {
            throw responseMismatch(
                    "Stripe refund does not match the requested amount",
                    result,
                    "provider_amount_mismatch"
            );
        }
        if (refund.getCurrency() == null || !command.currency().equalsIgnoreCase(refund.getCurrency())) {
            throw responseMismatch(
                    "Stripe refund does not match the requested currency",
                    result,
                    "provider_currency_mismatch"
            );
        }
        if (refund.getStatus() == null || refund.getStatus().isBlank()) {
            throw responseMismatch(
                    "Stripe returned a refund without a status",
                    result,
                    "provider_status_missing"
            );
        }
    }

    private StripeRefundProviderResponseException responseMismatch(
            String message,
            StripeRefundProviderResult result,
            String violationCode
    ) {
        return new StripeRefundProviderResponseException(message, result, violationCode);
    }
}
