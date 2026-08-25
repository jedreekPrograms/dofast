package com.doFast.dofastapp.user.dto;

import com.doFast.dofastapp.user.enums.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(
        @NotNull(message = "Status konta jest wymagany")
        UserStatus status
) {}
