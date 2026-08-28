package com.doFast.dofastapp.job.proposal;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record JobProposalResponse(
        Long id,
        Long jobId,
        Long proposerId,
        BigDecimal amount,
        String message,
        JobProposalStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime acceptedAt,
        LocalDateTime withdrawnAt
) {}
