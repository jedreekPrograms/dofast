package com.doFast.dofastapp.wallet.entity;

import com.doFast.dofastapp.wallet.enums.WalletTransactionType;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
public class WalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Wallet wallet;

    @Enumerated(EnumType.STRING)
    private WalletTransactionType type;

    private BigDecimal amount;

    private Long jobId;

    private LocalDateTime createdAt;

    public WalletTransaction() {}

    public WalletTransaction(Wallet wallet,
                             WalletTransactionType type,
                             BigDecimal amount,
                             Long jobId) {
        this.wallet = wallet;
        this.type = type;
        this.amount = amount;
        this.jobId = jobId;
        this.createdAt = LocalDateTime.now();
    }

    public WalletTransactionType getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public Long getJobId() { return jobId; }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
