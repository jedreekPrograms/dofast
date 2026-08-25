package com.doFast.dofastapp.wallet.entity;

import com.doFast.dofastapp.wallet.enums.WalletTransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "wallet_transactions",
        indexes = {
                @Index(name = "idx_wallet_transactions_wallet_created", columnList = "wallet_id,created_at"),
                @Index(name = "idx_wallet_transactions_job", columnList = "job_id")
        }
)
public class WalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WalletTransactionType type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "job_id")
    private Long jobId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public WalletTransaction() {}

    public WalletTransaction(Wallet wallet, WalletTransactionType type, BigDecimal amount, Long jobId) {
        this.wallet = wallet;
        this.type = type;
        this.amount = amount;
        this.jobId = jobId;
        this.createdAt = LocalDateTime.now();
    }

    public WalletTransactionType getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public Long getJobId() { return jobId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
