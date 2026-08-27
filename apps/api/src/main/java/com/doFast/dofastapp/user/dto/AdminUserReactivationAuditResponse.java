package com.doFast.dofastapp.user.dto;

import com.doFast.dofastapp.user.enums.UserStatus;

import java.time.LocalDateTime;

public record AdminUserReactivationAuditResponse(
        Long id,
        Long userId,
        Long adminId,
        String adminEmail,
        String adminNickname,
        UserStatus previousStatus,
        UserStatus newStatus,
        LocalDateTime createdAt
) {
}
