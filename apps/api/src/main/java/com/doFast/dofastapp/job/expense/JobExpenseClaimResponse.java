package com.doFast.dofastapp.job.expense;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record JobExpenseClaimResponse(
        Long id,
        BigDecimal amount,
        Long attachmentId,
        Long workerId,
        LocalDateTime createdAt
) {}
