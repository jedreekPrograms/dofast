package com.doFast.dofastapp.job.report;

import com.doFast.dofastapp.common.dto.PageResponse;
import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AdminJobReportService {

    private final JobReportRepository repository;
    private final JobReportEnforcementRepository enforcementRepository;

    public AdminJobReportService(
            JobReportRepository repository,
            JobReportEnforcementRepository enforcementRepository
    ) {
        this.repository = repository;
        this.enforcementRepository = enforcementRepository;
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

        String note = normalize(request.note());
        report.moderate(request.status(), moderator, note);
        return AdminJobReportResponse.from(report);
    }

    @Transactional
    public JobReportEnforcementResponse enforce(Long id, EnforceJobReportRequest request, User moderator) {
        JobReport report = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Zgłoszenie nie istnieje"));

        if (report.getStatus() != JobReportStatus.REVIEWED) {
            throw new ConflictException("Akcję egzekucyjną można wykonać tylko dla potwierdzonego zgłoszenia");
        }
        if (enforcementRepository.existsByReport_Id(id)) {
            throw new ConflictException("Dla tego zgłoszenia wykonano już akcję egzekucyjną");
        }
        if (request.action() != JobReportEnforcementAction.CANCEL_OPEN_JOB) {
            throw new ConflictException("Nieobsługiwana akcja egzekucyjna");
        }

        Job job = report.getJob();
        if (job.getStatus() != JobStatus.OPEN) {
            throw new ConflictException("Moderacyjne anulowanie jest dozwolone wyłącznie dla otwartego zlecenia");
        }

        job.cancel(LocalDateTime.now());
        JobReportEnforcement enforcement = new JobReportEnforcement(
                report,
                job,
                moderator,
                request.action(),
                normalize(request.reason())
        );
        enforcementRepository.save(enforcement);
        return JobReportEnforcementResponse.from(enforcement);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
