package com.doFast.dofastapp.payment.entity;

import com.doFast.dofastapp.common.enums.TransactionStatus;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.user.entity.User;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "escrow_transactions",
        uniqueConstraints = @UniqueConstraint(name = "uk_escrow_transactions_job", columnNames = "job_id"),
        indexes = {
                @Index(name = "idx_escrow_transactions_payer", columnList = "payer_id"),
                @Index(name = "idx_escrow_transactions_payee", columnList = "payee_id"),
                @Index(name = "idx_escrow_transactions_status", columnList = "status")
        }
)
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    private int version;

    @OneToOne(optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @ManyToOne(optional = false)
    @JoinColumn(name = "payer_id", nullable = false)
    private User payer;

    @ManyToOne
    @JoinColumn(name = "payee_id")
    private User payee;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TransactionStatus status;

    @Column(name = "platform_fee_basis_points", nullable = false)
    private int platformFeeBasisPoints;

    @Column(name = "platform_fee_amount", precision = 19, scale = 2)
    private BigDecimal platformFeeAmount;

    @Column(name = "payee_amount", precision = 19, scale = 2)
    private BigDecimal payeeAmount;

    @Column(name = "held_at", nullable = false)
    private LocalDateTime heldAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    public Transaction() {}

    public Long getId() { return id; }
    public Job getJob() { return job; }
    public User getPayer() { return payer; }
    public User getPayee() { return payee; }
    public BigDecimal getAmount() { return amount; }
    public TransactionStatus getStatus() { return status; }
    public int getPlatformFeeBasisPoints() { return platformFeeBasisPoints; }
    public BigDecimal getPlatformFeeAmount() { return platformFeeAmount; }
    public BigDecimal getPayeeAmount() { return payeeAmount; }
    public LocalDateTime getHeldAt() { return heldAt; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }

    public void initializeHeld(
            Job job,
            User payer,
            BigDecimal amount,
            int platformFeeBasisPoints,
            LocalDateTime at
    ) {
        if (platformFeeBasisPoints < 0 || platformFeeBasisPoints > 1000) {
            throw new IllegalArgumentException("Platform fee basis points are invalid");
        }
        this.job = job;
        this.payer = payer;
        this.amount = amount;
        this.status = TransactionStatus.HELD;
        this.platformFeeBasisPoints = platformFeeBasisPoints;
        this.platformFeeAmount = null;
        this.payeeAmount = null;
        this.heldAt = at;
        this.resolvedAt = null;
        this.payee = null;
    }

    public void adjustHeldAmount(BigDecimal amount) {
        if (status != TransactionStatus.HELD) {
            throw new IllegalStateException("Only held escrow can change amount");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Escrow amount must be positive");
        }
        this.amount = amount;
    }

    public void releaseTo(
            User payee,
            BigDecimal platformFeeAmount,
            BigDecimal payeeAmount,
            LocalDateTime at
    ) {
        if (status != TransactionStatus.HELD) {
            throw new IllegalStateException("Only held escrow can be released");
        }
        if (payee == null || platformFeeAmount == null || payeeAmount == null) {
            throw new IllegalArgumentException("Settlement breakdown is required");
        }
        if (platformFeeAmount.signum() < 0 || payeeAmount.signum() <= 0
                || platformFeeAmount.add(payeeAmount).compareTo(amount) != 0) {
            throw new IllegalArgumentException("Settlement breakdown does not match escrow amount");
        }
        this.payee = payee;
        this.platformFeeAmount = platformFeeAmount;
        this.payeeAmount = payeeAmount;
        this.status = TransactionStatus.RELEASED;
        this.resolvedAt = at;
    }

    public void refund(LocalDateTime at) {
        this.payee = null;
        this.platformFeeAmount = null;
        this.payeeAmount = null;
        this.status = TransactionStatus.REFUNDED;
        this.resolvedAt = at;
    }
}
