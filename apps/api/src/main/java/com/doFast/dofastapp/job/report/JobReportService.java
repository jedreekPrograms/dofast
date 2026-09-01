package com.doFast.dofastapp.job.report;

import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.repository.JobRepository;
import com.doFast.dofastapp.user.entity.User;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class JobReportService {

    private final JobReportRepository reportRepository;
    private final JobRepository jobRepository;

    public JobReportService(JobReportRepository reportRepository, JobRepository jobRepository) {
        this.reportRepository = reportRepository;
        this.jobRepository = jobRepository;
    }

    @Transactional
    public JobReportResponse report(Long jobId, JobReportRequest request, User reporter) {
        Job job = jobRepository.findByIdAndStatus(jobId, JobStatus.OPEN)
                .orElseThrow(() -> new ResourceNotFoundException("Zlecenie nie istnieje"));

        if (job.getCreatedBy() != null && job.getCreatedBy().getId().equals(reporter.getId())) {
            throw new ForbiddenOperationException("Nie możesz zgłosić własnego zlecenia");
        }
        if (reportRepository.existsByReporter_IdAndJob_Id(reporter.getId(), jobId)) {
            throw new ConflictException("To zlecenie zostało już przez Ciebie zgłoszone");
        }

        String details = request.details() == null ? null : request.details().trim();
        if (details != null && details.isEmpty()) {
            details = null;
        }

        JobReport saved;
        try {
            saved = reportRepository.saveAndFlush(new JobReport(job, reporter, request.reason(), details));
        } catch (DataIntegrityViolationException exception) {
            // The unique reporter/job constraint is the final authority under concurrent requests.
            // Translate the race into the same stable API contract as the optimistic pre-check
            // instead of leaking a persistence exception as an internal server error.
            throw new ConflictException("To zlecenie zostało już przez Ciebie zgłoszone");
        }

        return JobReportResponse.from(saved);
    }

    @Transactional
    public JobReportResponse withdraw(Long reportId, User reporter) {
        JobReport report = reportRepository.findByIdAndReporter_Id(reportId, reporter.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Zgłoszenie nie istnieje"));

        if (report.getStatus() != JobReportStatus.SUBMITTED) {
            throw new ConflictException("Możesz wycofać tylko zgłoszenie oczekujące na moderację");
        }

        report.withdraw();
        return JobReportResponse.from(reportRepository.save(report));
    }

    @Transactional(readOnly = true)
    public List<JobReportResponse> mine(User reporter) {
        return reportRepository.findAllByReporter_IdOrderByCreatedAtDesc(reporter.getId())
                .stream()
                .map(JobReportResponse::from)
                .toList();
    }
}
