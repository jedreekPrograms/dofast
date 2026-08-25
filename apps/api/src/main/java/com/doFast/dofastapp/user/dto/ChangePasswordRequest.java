package com.doFast.dofastapp.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Aktualne hasło nie może być puste")
        String currentPassword,
        @NotBlank(message = "Nowe hasło nie może być puste")
        @Size(min = 8, max = 72, message = "Nowe hasło musi mieć od 8 do 72 znaków")
        String newPassword
) {}
