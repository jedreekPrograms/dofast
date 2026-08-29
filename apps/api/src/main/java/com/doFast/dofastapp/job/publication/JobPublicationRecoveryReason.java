package com.doFast.dofastapp.job.publication;

public enum JobPublicationRecoveryReason {
    PUBLICATION_EXPIRED,
    CATEGORY_UNAVAILABLE,
    ROUTE_QUOTE_UNAVAILABLE,
    CANCELLED_BEFORE_PAYMENT_CONFIRMED,
    UNSPECIFIED
}
