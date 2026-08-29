package com.doFast.dofastapp.payment.refund.service;

public record StripeRefundProviderResult(
        String refundId,
        String status,
        String failureReason
) {
}
