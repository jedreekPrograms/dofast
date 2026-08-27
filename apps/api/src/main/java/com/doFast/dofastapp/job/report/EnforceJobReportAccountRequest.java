package com.doFast.dofastapp.job.report;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EnforceJobReportAccountRequest(
        @NotNull JobReportAccountEnforcementAction action,
        @Size(max = 1000) String reason
) {}
