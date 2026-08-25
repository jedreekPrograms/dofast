package com.doFast.dofastapp.payment.dto;

import java.math.BigDecimal;

public record CreatePaymentIntentResponse(
        String paymentIntentId,
        String clientSecret,
        BigDecimal amount,
        String currency
) {}
