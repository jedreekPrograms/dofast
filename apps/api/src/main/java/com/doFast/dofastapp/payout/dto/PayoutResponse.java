package com.doFast.dofastapp.payout.dto;

import com.doFast.dofastapp.payout.enums.PayoutStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PayoutResponse(
        Long id,
        BigDecimal amount,
        String currency,
        PayoutStatus status,
        String providerMode,
        int attemptCount,
        LocalDateTime requestedAt,
        LocalDateTime resolvedAt,
        boolean cancellable
) {}
