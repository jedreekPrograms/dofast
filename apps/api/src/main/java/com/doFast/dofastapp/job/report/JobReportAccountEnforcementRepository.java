package com.doFast.dofastapp.job.report;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JobReportAccountEnforcementRepository extends JpaRepository<JobReportAccountEnforcement, Long> {
    boolean existsByReport_Id(Long reportId);
    Optional<JobReportAccountEnforcement> findByReport_Id(Long reportId);
}
