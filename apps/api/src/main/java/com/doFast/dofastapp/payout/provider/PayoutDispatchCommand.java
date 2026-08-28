package com.doFast.dofastapp.payout.provider;

import java.math.BigDecimal;

public record PayoutDispatchCommand(
        Long payoutId,
        Long userId,
        BigDecimal amount,
        String currency,
        String providerCode,
        String idempotencyKey,
        int attempt
) {}
