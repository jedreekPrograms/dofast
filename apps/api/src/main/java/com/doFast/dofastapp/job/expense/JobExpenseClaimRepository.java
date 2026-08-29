package com.doFast.dofastapp.job.expense;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobExpenseClaimRepository extends JpaRepository<JobExpenseClaim, Long> {
    List<JobExpenseClaim> findAllByJob_IdOrderByCreatedAtAscIdAsc(Long jobId);
    boolean existsByAttachment_Id(Long attachmentId);
}
