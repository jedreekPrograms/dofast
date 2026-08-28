package com.doFast.dofastapp.payout.dto;

import java.math.BigDecimal;

public record PayoutEligibilityResponse(
        boolean identityVerified,
        boolean providerAvailable,
        String providerMode,
        BigDecimal minimumAmount,
        BigDecimal availableBalance,
        String currency,
        boolean eligible
) {}
