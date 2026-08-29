package com.doFast.dofastapp.dispute.dto;

import com.doFast.dofastapp.job.expense.JobExpenseSummaryResponse;

import java.util.List;

public record DisputeDetailResponse(
        DisputeResponse dispute,
        List<DisputeEventResponse> events,
        JobExpenseSummaryResponse expenseSummary
) {}
