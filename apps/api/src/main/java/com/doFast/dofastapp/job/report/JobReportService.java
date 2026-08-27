package com.doFast.dofastapp.job.report;

import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.repository.JobRepository;
import com.doFast.dofastapp.user.entity.User;
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
        Job job = jobRepository.findById(jobId)
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

        return JobReportResponse.from(
                reportRepository.save(new JobReport(job, reporter, request.reason(), details))
        );
    }

    @Transactional(readOnly = true)
    public List<JobReportResponse> mine(User reporter) {
        return reportRepository.findAllByReporter_IdOrderByCreatedAtDesc(reporter.getId())
                .stream()
                .map(JobReportResponse::from)
                .toList();
    }
}
