package com.doFast.dofastapp.payout.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminPayoutFailureRequest(
        @NotBlank
        @Size(max = 500)
        String reason
) {}
