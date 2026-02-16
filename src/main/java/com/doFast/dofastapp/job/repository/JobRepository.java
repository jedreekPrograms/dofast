package com.doFast.dofastapp.job.repository;

import com.doFast.dofastapp.job.entity.Job;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobRepository extends JpaRepository<Job, Long> {
}
