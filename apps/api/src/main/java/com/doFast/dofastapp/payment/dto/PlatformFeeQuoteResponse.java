package com.doFast.dofastapp.payment.dto;

import java.math.BigDecimal;

public record PlatformFeeQuoteResponse(
        BigDecimal grossAmount,
        BigDecimal platformFeeAmount,
        BigDecimal workerPayoutAmount,
        int basisPoints,
        BigDecimal percent
) {}
