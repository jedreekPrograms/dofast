package com.doFast.dofastapp.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FinanceReconciliationResponse(
        boolean healthy,
        long walletBalanceMismatches,
        long ledgerSequenceMismatches,
        long stripeLedgerMismatches,
        long platformRevenueMismatches,
        long heldEscrowCount,
        BigDecimal heldEscrowAmount,
        BigDecimal platformFeeRevenueAmount,
        long processedStripePayments,
        LocalDateTime checkedAt
) {}
