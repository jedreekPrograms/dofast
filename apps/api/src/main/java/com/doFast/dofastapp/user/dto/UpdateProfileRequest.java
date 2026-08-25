package com.doFast.dofastapp.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank(message = "Nickname nie może być pusty")
        @Size(min = 3, max = 80, message = "Nickname musi mieć od 3 do 80 znaków")
        String nickname
) {}
