package com.doFast.dofastapp.wallet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
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
        name = "wallet_funding_movements",
        indexes = {
                @Index(name = "idx_wallet_funding_movements_transaction", columnList = "wallet_transaction_id,id"),
                @Index(name = "idx_wallet_funding_movements_lot", columnList = "funding_lot_id,id"),
                @Index(name = "idx_wallet_funding_movements_restore", columnList = "restores_movement_id")
        }
)
public class WalletFundingMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_transaction_id", nullable = false)
    private WalletTransaction walletTransaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "funding_lot_id", nullable = false)
    private WalletFundingLot fundingLot;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restores_movement_id")
    private WalletFundingMovement restoresMovement;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public WalletFundingMovement() {}

    public WalletFundingMovement(
            WalletTransaction walletTransaction,
            WalletFundingLot fundingLot,
            BigDecimal amount,
            WalletFundingMovement restoresMovement,
            LocalDateTime createdAt
    ) {
        if (walletTransaction == null || fundingLot == null || amount == null || amount.signum() == 0 || createdAt == null) {
            throw new IllegalArgumentException("Invalid wallet funding movement");
        }
        if (restoresMovement != null && amount.signum() <= 0) {
            throw new IllegalArgumentException("A restoration movement must be positive");
        }
        this.walletTransaction = walletTransaction;
        this.fundingLot = fundingLot;
        this.amount = amount;
        this.restoresMovement = restoresMovement;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public WalletTransaction getWalletTransaction() { return walletTransaction; }
    public WalletFundingLot getFundingLot() { return fundingLot; }
    public BigDecimal getAmount() { return amount; }
    public WalletFundingMovement getRestoresMovement() { return restoresMovement; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
