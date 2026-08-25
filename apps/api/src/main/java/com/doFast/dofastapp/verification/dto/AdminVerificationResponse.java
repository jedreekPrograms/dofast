package com.doFast.dofastapp.verification.dto;

import com.doFast.dofastapp.verification.enums.VerificationStatus;

import java.time.LocalDateTime;

public record AdminVerificationResponse(
        Long id,
        Long userId,
        String email,
        String nickname,
        VerificationStatus status,
        String provider,
        String providerReference,
        LocalDateTime requestedAt,
        LocalDateTime reviewedAt,
        LocalDateTime verifiedAt,
        LocalDateTime revokedAt,
        Long reviewedByUserId,
        String decisionReason,
        LocalDateTime updatedAt
) {}
