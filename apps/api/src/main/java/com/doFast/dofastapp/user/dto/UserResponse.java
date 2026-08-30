package com.doFast.dofastapp.user.dto;

import com.doFast.dofastapp.user.enums.UserRole;
import com.doFast.dofastapp.user.enums.UserStatus;

import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String email,
        String nickname,
        String bio,
        String publicLocation,
        UserRole role,
        UserStatus status,
        boolean emailVerified,
        LocalDateTime createdAt
) {}
