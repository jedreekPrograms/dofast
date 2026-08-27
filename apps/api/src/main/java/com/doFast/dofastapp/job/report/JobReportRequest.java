package com.doFast.dofastapp.job.report;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record JobReportRequest(
        @NotNull JobReportReason reason,
        @Size(max = 1000) String details
) {}
