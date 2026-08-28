package com.doFast.dofastapp.job.publication.dto;

import com.doFast.dofastapp.job.publication.JobPublicationStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record JobPublicationResponse(
        Long id,
        JobPublicationStatus status,
        BigDecimal totalAmount,
        BigDecimal walletReservedAmount,
        BigDecimal missingAmount,
        BigDecimal paymentAmount,
        String currency,
        Long jobId,
        LocalDateTime expiresAt,
        boolean paymentRequired,
        boolean cancellable
) {}
