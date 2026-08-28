package com.doFast.dofastapp.payout.dto;

import com.doFast.dofastapp.payout.enums.PayoutEventSource;
import com.doFast.dofastapp.payout.enums.PayoutEventType;

import java.time.LocalDateTime;

public record AdminPayoutEventResponse(
        Long id,
        PayoutEventType eventType,
        PayoutEventSource source,
        Long actorUserId,
        String actorNickname,
        String note,
        LocalDateTime createdAt
) {}
