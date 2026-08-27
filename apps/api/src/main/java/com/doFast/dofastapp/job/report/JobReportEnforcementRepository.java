package com.doFast.dofastapp.job.report;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JobReportEnforcementRepository extends JpaRepository<JobReportEnforcement, Long> {
    boolean existsByReport_Id(Long reportId);
    Optional<JobReportEnforcement> findByReport_Id(Long reportId);
}
