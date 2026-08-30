package com.doFast.dofastapp.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ForgotPasswordRequest(
        @NotBlank(message = "Email nie może być pusty")
        @Email(message = "Niepoprawny format email")
        @Size(max = 320, message = "Email jest za długi")
        String email
) {}
