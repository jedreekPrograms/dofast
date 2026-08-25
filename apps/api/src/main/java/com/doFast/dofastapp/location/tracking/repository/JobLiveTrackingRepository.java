package com.doFast.dofastapp.location.tracking.repository;

import com.doFast.dofastapp.location.tracking.entity.JobLiveTracking;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface JobLiveTrackingRepository extends JpaRepository<JobLiveTracking, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from JobLiveTracking t where t.jobId = :jobId")
    Optional<JobLiveTracking> findByJobIdForUpdate(@Param("jobId") Long jobId);
}
