package com.doFast.dofastapp.job.report;

import java.time.LocalDateTime;

public record JobReportResponse(
        Long id,
        Long jobId,
        JobReportReason reason,
        String details,
        JobReportStatus status,
        LocalDateTime createdAt,
        LocalDateTime reviewedAt,
        LocalDateTime withdrawnAt
) {
    static JobReportResponse from(JobReport report) {
        return new JobReportResponse(
                report.getId(),
                report.getJob().getId(),
                report.getReason(),
                report.getDetails(),
                report.getStatus(),
                report.getCreatedAt(),
                report.getReviewedAt(),
                report.getWithdrawnAt()
        );
    }
}
