package com.doFast.dofastapp.job.report;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JobReportEnforcementRepository extends JpaRepository<JobReportEnforcement, Long> {
    boolean existsByReport_Id(Long reportId);
}
