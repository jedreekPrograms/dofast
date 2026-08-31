package com.doFast.dofastapp.payout.dto;

import com.doFast.dofastapp.payout.enums.PayoutStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminPayoutResponse(
        Long id,
        Long userId,
        String userNickname,
        BigDecimal amount,
        String currency,
        PayoutStatus status,
        String providerCode,
        String providerReference,
        String providerTransferReference,
        int attemptCount,
        String failureCode,
        LocalDateTime requestedAt,
        LocalDateTime processingStartedAt,
        LocalDateTime resolvedAt
) {}
