package com.doFast.dofastapp.job.report;

import java.time.LocalDateTime;

public record AdminJobReportResponse(
        Long id,
        Long jobId,
        Long reporterId,
        String reporterEmail,
        JobReportReason reason,
        String details,
        JobReportStatus status,
        LocalDateTime createdAt,
        LocalDateTime reviewedAt,
        Long reviewedById,
        String moderationNote
) {
    static AdminJobReportResponse from(JobReport report) {
        return new AdminJobReportResponse(
                report.getId(),
                report.getJob().getId(),
                report.getReporter().getId(),
                report.getReporter().getEmail(),
                report.getReason(),
                report.getDetails(),
                report.getStatus(),
                report.getCreatedAt(),
                report.getReviewedAt(),
                report.getReviewedBy() == null ? null : report.getReviewedBy().getId(),
                report.getModerationNote()
        );
    }
}
