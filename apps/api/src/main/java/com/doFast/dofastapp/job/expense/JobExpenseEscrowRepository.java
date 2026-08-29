package com.doFast.dofastapp.job.expense;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface JobExpenseEscrowRepository extends JpaRepository<JobExpenseEscrow, Long> {
    Optional<JobExpenseEscrow> findByJob_Id(Long jobId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from JobExpenseEscrow e where e.job.id = :jobId")
    Optional<JobExpenseEscrow> findByJobIdForUpdate(@Param("jobId") Long jobId);
}
