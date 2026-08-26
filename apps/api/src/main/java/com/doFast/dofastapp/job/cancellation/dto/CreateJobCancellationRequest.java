package com.doFast.dofastapp.job.cancellation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateJobCancellationRequest(
        @NotBlank @Size(max = 1000) String reason
) {}
