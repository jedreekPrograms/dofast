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
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "wallet_transactions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_wallet_transactions_operation",
                columnNames = "operation_key"
        ),
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

    @Column(name = "balance_after", nullable = false, precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "operation_key", nullable = false, length = 160)
    private String operationKey;

    @Column(name = "job_id")
    private Long jobId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public WalletTransaction() {}

    public WalletTransaction(
            Wallet wallet,
            WalletTransactionType type,
            BigDecimal amount,
            BigDecimal balanceAfter,
            String operationKey,
            Long jobId
    ) {
        this.wallet = wallet;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.operationKey = operationKey;
        this.jobId = jobId;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Wallet getWallet() { return wallet; }
    public WalletTransactionType getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public String getOperationKey() { return operationKey; }
    public Long getJobId() { return jobId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
