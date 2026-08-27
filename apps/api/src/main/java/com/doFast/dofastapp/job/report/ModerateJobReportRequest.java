package com.doFast.dofastapp.job.report;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ModerateJobReportRequest(
        @NotNull JobReportStatus status,
        @Size(max = 1000) String note
) {}
