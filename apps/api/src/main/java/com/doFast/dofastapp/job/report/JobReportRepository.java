package com.doFast.dofastapp.job.report;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobReportRepository extends JpaRepository<JobReport, Long> {
    boolean existsByReporter_IdAndJob_Id(Long reporterId, Long jobId);
    Optional<JobReport> findByIdAndReporter_Id(Long id, Long reporterId);
    List<JobReport> findAllByReporter_IdOrderByCreatedAtDesc(Long reporterId);
    Page<JobReport> findAllByOrderByCreatedAtAsc(Pageable pageable);
    Page<JobReport> findAllByStatusOrderByCreatedAtAsc(JobReportStatus status, Pageable pageable);
}
