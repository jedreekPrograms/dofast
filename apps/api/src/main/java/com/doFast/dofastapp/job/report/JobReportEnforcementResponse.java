package com.doFast.dofastapp.job.report;

import java.time.LocalDateTime;

public record JobReportEnforcementResponse(
        Long id,
        Long reportId,
        Long jobId,
        Long moderatorId,
        JobReportEnforcementAction action,
        String reason,
        LocalDateTime createdAt
) {
    static JobReportEnforcementResponse from(JobReportEnforcement enforcement) {
        return new JobReportEnforcementResponse(
                enforcement.getId(),
                enforcement.getReport().getId(),
                enforcement.getJob().getId(),
                enforcement.getModerator().getId(),
                enforcement.getAction(),
                enforcement.getReason(),
                enforcement.getCreatedAt()
        );
    }
}
