package com.doFast.dofastapp.dispute.dto;

import com.doFast.dofastapp.dispute.enums.DisputeResolution;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ResolveDisputeRequest(
        @NotNull DisputeResolution resolution,
        @NotBlank @Size(max = 4000) String note
) {}
