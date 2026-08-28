package com.doFast.dofastapp.payout.enums;

public enum PayoutEventType {
    REQUESTED,
    PROCESSING_STARTED,
    RETRY_SCHEDULED,
    REVIEW_REQUIRED,
    PAID,
    FAILED,
    CANCELLED,
    FUNDS_RESTORED,
    ADMIN_RETRY
}
