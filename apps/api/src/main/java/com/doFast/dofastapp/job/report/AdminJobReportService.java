package com.doFast.dofastapp.job.report;

import com.doFast.dofastapp.common.dto.PageResponse;
import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
import com.doFast.dofastapp.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminJobReportService {

    private final JobReportRepository repository;

    public AdminJobReportService(JobReportRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminJobReportResponse> list(JobReportStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        Page<JobReport> reports = status == null
                ? repository.findAllByOrderByCreatedAtAsc(pageable)
                : repository.findAllByStatusOrderByCreatedAtAsc(status, pageable);
        return PageResponse.from(reports, reports.stream().map(AdminJobReportResponse::from).toList());
    }

    @Transactional
    public AdminJobReportResponse moderate(Long id, ModerateJobReportRequest request, User moderator) {
        if (request.status() == JobReportStatus.SUBMITTED) {
            throw new ConflictException("Moderation decision must be REVIEWED or DISMISSED");
        }

        JobReport report = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Zgłoszenie nie istnieje"));
        if (report.getStatus() != JobReportStatus.SUBMITTED) {
            throw new ConflictException("Zgłoszenie zostało już rozpatrzone");
        }

        String note = request.note() == null ? null : request.note().trim();
        if (note != null && note.isEmpty()) {
            note = null;
        }
        report.moderate(request.status(), moderator, note);
        return AdminJobReportResponse.from(report);
    }
}
