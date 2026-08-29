package com.doFast.dofastapp.payment.refund.service;

import java.math.BigDecimal;

public record StripeRefundDispatchCommand(
        Long requestId,
        Long userId,
        String paymentIntentId,
        BigDecimal amount,
        String currency,
        int attempt
) {
}
