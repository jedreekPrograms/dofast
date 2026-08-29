package com.doFast.dofastapp.payment.refund.entity;

public enum StripeRefundStatus {
    REQUESTED,
    DISPATCHING,
    PENDING,
    REQUIRES_ACTION,
    SUCCEEDED,
    FAILED,
    CANCELED
}
