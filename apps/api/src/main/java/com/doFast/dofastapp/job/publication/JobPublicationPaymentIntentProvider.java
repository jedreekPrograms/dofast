package com.doFast.dofastapp.job.publication;

import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import org.springframework.stereotype.Service;

@Service
public class JobPublicationPaymentIntentProvider {

    public PaymentIntent create(JobPublicationPaymentIntentCreateCommand command) throws StripeException {
        RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey(command.idempotencyKey())
                .build();
        return PaymentIntent.create(JobPublicationPaymentIntentService.paymentIntentParams(command), options);
    }

    public PaymentIntent retrieve(String paymentIntentId) throws StripeException {
        return PaymentIntent.retrieve(paymentIntentId);
    }
}
