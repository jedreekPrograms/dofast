package com.doFast.dofastapp.job.publication.dto;

import com.doFast.dofastapp.job.dto.JobRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateJobPublicationRequest(
        @NotBlank @Size(max = 80) String requestId,
        @NotNull @Valid JobRequest job
) {}
