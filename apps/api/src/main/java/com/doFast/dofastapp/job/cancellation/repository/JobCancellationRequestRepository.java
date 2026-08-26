package com.doFast.dofastapp.job.cancellation.repository;

import com.doFast.dofastapp.job.cancellation.entity.JobCancellationRequest;
import com.doFast.dofastapp.job.cancellation.enums.JobCancellationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface JobCancellationRequestRepository extends JpaRepository<JobCancellationRequest, Long> {

    Optional<JobCancellationRequest> findFirstByJob_IdAndStatusOrderByRequestedAtDesc(
            Long jobId,
            JobCancellationStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select request
            from JobCancellationRequest request
            where request.job.id = :jobId
              and request.status = :status
            """)
    Optional<JobCancellationRequest> findPendingForUpdate(
            @Param("jobId") Long jobId,
            @Param("status") JobCancellationStatus status
    );
}
