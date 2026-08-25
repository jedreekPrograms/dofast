package com.doFast.dofastapp.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FinanceReconciliationResponse(
        boolean healthy,
        long walletBalanceMismatches,
        long ledgerSequenceMismatches,
        long stripeLedgerMismatches,
        long heldEscrowCount,
        BigDecimal heldEscrowAmount,
        long processedStripePayments,
        LocalDateTime checkedAt
) {}
