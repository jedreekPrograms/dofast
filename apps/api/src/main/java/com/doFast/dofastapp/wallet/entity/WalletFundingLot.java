package com.doFast.dofastapp.wallet.entity;

import com.doFast.dofastapp.wallet.enums.WalletFundingSourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
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
        name = "wallet_funding_lots",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_wallet_funding_lot_source",
                columnNames = {"wallet_id", "source_type", "source_reference"}
        ),
        indexes = @Index(
                name = "idx_wallet_funding_lots_available",
                columnList = "wallet_id,withdrawable,created_at,id"
        )
)
public class WalletFundingLot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32)
    private WalletFundingSourceType sourceType;

    @Column(name = "source_reference", nullable = false, length = 255)
    private String sourceReference;

    @Column(name = "original_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal originalAmount;

    @Column(name = "remaining_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal remainingAmount;

    @Column(nullable = false)
    private boolean withdrawable;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public WalletFundingLot() {}

    public WalletFundingLot(
            Wallet wallet,
            WalletFundingSourceType sourceType,
            String sourceReference,
            BigDecimal amount,
            boolean withdrawable,
            LocalDateTime createdAt
    ) {
        if (wallet == null || sourceType == null || sourceReference == null || sourceReference.isBlank()) {
            throw new IllegalArgumentException("Funding lot source is required");
        }
        if (amount == null || amount.signum() <= 0 || createdAt == null) {
            throw new IllegalArgumentException("Funding lot amount and creation time are required");
        }
        this.wallet = wallet;
        this.sourceType = sourceType;
        this.sourceReference = sourceReference.trim();
        this.originalAmount = amount;
        this.remainingAmount = amount;
        this.withdrawable = withdrawable;
        this.createdAt = createdAt;
    }

    public void consume(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || remainingAmount.compareTo(amount) < 0) {
            throw new IllegalArgumentException("Invalid funding lot consumption");
        }
        remainingAmount = remainingAmount.subtract(amount);
    }

    public void restore(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Invalid funding lot restoration");
        }
        BigDecimal restored = remainingAmount.add(amount);
        if (restored.compareTo(originalAmount) > 0) {
            throw new IllegalStateException("Funding lot restoration exceeds original amount");
        }
        remainingAmount = restored;
    }

    public Long getId() { return id; }
    public Wallet getWallet() { return wallet; }
    public WalletFundingSourceType getSourceType() { return sourceType; }
    public String getSourceReference() { return sourceReference; }
    public BigDecimal getOriginalAmount() { return originalAmount; }
    public BigDecimal getRemainingAmount() { return remainingAmount; }
    public boolean isWithdrawable() { return withdrawable; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
