package com.doFast.dofastapp.user.auth.apple;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record AppleLoginRequest(
        @NotNull UUID challengeId,
        @NotBlank @Size(max = 10000) String code,
        @NotBlank @Size(max = 512) String state,
        @NotBlank @Size(max = 512) String nonce,
        @Size(max = 100) String firstName,
        @Size(max = 100) String lastName
) {}