package com.doFast.dofastapp.payment.fee;

import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.payment.entity.Transaction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "platform_revenue_entries")
public class PlatformRevenueEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "escrow_transaction_id", nullable = false)
    private Transaction escrowTransaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PlatformRevenueType type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "operation_key", nullable = false, length = 160, unique = true)
    private String operationKey;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected PlatformRevenueEntry() {}

    public PlatformRevenueEntry(
            Transaction escrowTransaction,
            Job job,
            PlatformRevenueType type,
            BigDecimal amount,
            String operationKey,
            LocalDateTime createdAt
    ) {
        this.escrowTransaction = escrowTransaction;
        this.job = job;
        this.type = type;
        this.amount = amount;
        this.operationKey = operationKey;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Transaction getEscrowTransaction() { return escrowTransaction; }
    public Job getJob() { return job; }
    public PlatformRevenueType getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public String getOperationKey() { return operationKey; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
