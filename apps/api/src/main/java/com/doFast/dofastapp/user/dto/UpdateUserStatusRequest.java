package com.doFast.dofastapp.user.dto;

import com.doFast.dofastapp.user.enums.UserStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateUserStatusRequest(
        @NotNull(message = "Status konta jest wymagany")
        UserStatus status,
        @NotBlank(message = "Powód reaktywacji jest wymagany")
        @Size(max = 1000, message = "Powód reaktywacji może mieć maksymalnie 1000 znaków")
        String reason
) {}
