package com.doFast.dofastapp.job.expense;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record JobExpenseSummaryResponse(
        Long jobId,
        BigDecimal budgetAmount,
        BigDecimal claimedAmount,
        BigDecimal reimbursedAmount,
        BigDecimal refundedAmount,
        JobExpenseEscrowStatus status,
        LocalDateTime heldAt,
        LocalDateTime resolvedAt,
        List<JobExpenseClaimResponse> claims
) {}
