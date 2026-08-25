package com.doFast.dofastapp.dispute.dto;

import com.doFast.dofastapp.dispute.enums.DisputeReason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateDisputeRequest(
        @NotNull Long jobId,
        @NotNull DisputeReason reason,
        @NotBlank @Size(max = 4000) String description
) {}
