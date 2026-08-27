package com.doFast.dofastapp.job.report;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobReportRepository extends JpaRepository<JobReport, Long> {
    boolean existsByReporter_IdAndJob_Id(Long reporterId, Long jobId);
    List<JobReport> findAllByReporter_IdOrderByCreatedAtDesc(Long reporterId);
}
