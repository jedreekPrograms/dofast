package com.doFast.dofastapp.payment.refund.dto;

import com.doFast.dofastapp.payment.refund.entity.StripeRefundStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record StripeRefundResponse(
        Long id,
        String paymentIntentId,
        BigDecimal amount,
        String currency,
        StripeRefundStatus status,
        String stripeRefundId,
        String failureReason,
        int attemptCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime resolvedAt
) {
}
