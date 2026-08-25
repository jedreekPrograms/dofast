package com.doFast.dofastapp.wallet.dto;

import com.doFast.dofastapp.wallet.enums.WalletTransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class WalletTransactionResponse {

    private final WalletTransactionType type;
    private final BigDecimal amount;
    private final BigDecimal balanceAfter;
    private final LocalDateTime createdAt;
    private final Long jobId;

    public WalletTransactionResponse(
            WalletTransactionType type,
            BigDecimal amount,
            BigDecimal balanceAfter,
            LocalDateTime createdAt,
            Long jobId
    ) {
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.createdAt = createdAt;
        this.jobId = jobId;
    }

    public WalletTransactionType getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public Long getJobId() { return jobId; }
}
