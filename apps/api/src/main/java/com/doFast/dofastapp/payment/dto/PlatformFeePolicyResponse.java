package com.doFast.dofastapp.payment.dto;

import java.math.BigDecimal;

public record PlatformFeePolicyResponse(
        int basisPoints,
        BigDecimal percent
) {}
