package com.doFast.dofastapp.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "Token resetu hasła nie może być pusty")
        @Size(max = 200, message = "Token resetu hasła jest nieprawidłowy")
        String token,
        @NotBlank(message = "Nowe hasło nie może być puste")
        @Size(min = 8, max = 72, message = "Nowe hasło musi mieć od 8 do 72 znaków")
        String newPassword
) {}
