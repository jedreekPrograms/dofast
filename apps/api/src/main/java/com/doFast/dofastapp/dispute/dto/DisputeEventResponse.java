package com.doFast.dofastapp.dispute.dto;

import com.doFast.dofastapp.dispute.enums.DisputeEventType;

import java.time.LocalDateTime;

public record DisputeEventResponse(
        Long id,
        Long actorId,
        String actorNickname,
        DisputeEventType eventType,
        String note,
        LocalDateTime createdAt
) {}
