package com.doFast.dofastapp.job.report;

import java.time.LocalDateTime;

public record JobReportAccountEnforcementResponse(
        Long id,
        Long reportId,
        Long targetUserId,
        Long moderatorId,
        JobReportAccountEnforcementAction action,
        String reason,
        LocalDateTime createdAt
) {
    static JobReportAccountEnforcementResponse from(JobReportAccountEnforcement enforcement) {
        return new JobReportAccountEnforcementResponse(
                enforcement.getId(),
                enforcement.getReport().getId(),
                enforcement.getTargetUser().getId(),
                enforcement.getModerator().getId(),
                enforcement.getAction(),
                enforcement.getReason(),
                enforcement.getCreatedAt()
        );
    }
}
