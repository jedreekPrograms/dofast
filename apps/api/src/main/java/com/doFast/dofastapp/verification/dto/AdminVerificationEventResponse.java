package com.doFast.dofastapp.verification.dto;

import com.doFast.dofastapp.verification.enums.VerificationEventType;
import com.doFast.dofastapp.verification.enums.VerificationStatus;

import java.time.LocalDateTime;

public record AdminVerificationEventResponse(
        Long id,
        VerificationEventType eventType,
        VerificationStatus fromStatus,
        VerificationStatus toStatus,
        Long actorUserId,
        String actorNickname,
        String reason,
        LocalDateTime createdAt
) {}
