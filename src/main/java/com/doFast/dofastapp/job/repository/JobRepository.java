package com.doFast.dofastapp.job.repository;

import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.job.entity.Job;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobRepository extends JpaRepository<Job, Long> {
    List<Job> findByStatus(JobStatus status);
}
