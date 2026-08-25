package com.doFast.dofastapp.verification.dto;

import com.doFast.dofastapp.verification.enums.VerificationStatus;

import java.time.LocalDateTime;

public record VerificationResponse(
        Long id,
        VerificationStatus status,
        LocalDateTime requestedAt,
        LocalDateTime reviewedAt,
        LocalDateTime verifiedAt,
        LocalDateTime revokedAt,
        String decisionReason,
        boolean canRequest
) {
    public static VerificationResponse notStarted() {
        return new VerificationResponse(
                null,
                VerificationStatus.NOT_STARTED,
                null,
                null,
                null,
                null,
                null,
                true
        );
    }
}
