package com.doFast.dofastapp.payment.fee;

import java.math.BigDecimal;

public record PlatformFeeQuote(
        BigDecimal grossAmount,
        BigDecimal platformFeeAmount,
        BigDecimal workerPayoutAmount,
        int basisPoints
) {}
