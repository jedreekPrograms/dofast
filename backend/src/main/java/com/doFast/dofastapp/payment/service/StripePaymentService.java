package com.doFast.dofastapp.payment.service;

import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class StripePaymentService {

    public PaymentIntent createPaymentIntent(BigDecimal amount, Long userId) throws Exception {

        PaymentIntentCreateParams params =
                PaymentIntentCreateParams.builder()
                        .setAmount(amount.multiply(BigDecimal.valueOf(100)).longValue())
                        .setCurrency("pln")
                        .putMetadata("userId", userId.toString())
                        .setAutomaticPaymentMethods(
                                PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                        .setEnabled(true)
                                        .setAllowRedirects(
                                                PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.NEVER
                                        )
                                        .build()
                        )
                        .build();

        return PaymentIntent.create(params);
    }
}
