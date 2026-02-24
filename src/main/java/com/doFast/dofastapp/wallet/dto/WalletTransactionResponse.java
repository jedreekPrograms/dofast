package com.doFast.dofastapp.wallet.dto;

import com.doFast.dofastapp.wallet.enums.WalletTransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class WalletTransactionResponse {

    private WalletTransactionType type;
    private BigDecimal amount;
    private LocalDateTime createdAt;
    private Long jobId;

    public WalletTransactionResponse(WalletTransactionType type,
                                     BigDecimal amount,
                                     LocalDateTime createdAt,
                                     Long jobId) {
        this.type = type;
        this.amount = amount;
        this.createdAt = createdAt;
        this.jobId = jobId;
    }

    public WalletTransactionType getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public Long getJobId() { return jobId; }
}
