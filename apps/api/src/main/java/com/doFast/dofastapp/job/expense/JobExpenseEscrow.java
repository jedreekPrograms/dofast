package com.doFast.dofastapp.job.expense;

import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.user.entity.User;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "job_expense_escrows")
public class JobExpenseEscrow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    private int version;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false, unique = true)
    private Job job;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payer_id", nullable = false)
    private User payer;

    @Column(name = "budget_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal budgetAmount;

    @Column(name = "claimed_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal claimedAmount;

    @Column(name = "reimbursed_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal reimbursedAmount;

    @Column(name = "refunded_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal refundedAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private JobExpenseEscrowStatus status;

    @Column(name = "held_at", nullable = false)
    private LocalDateTime heldAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    protected JobExpenseEscrow() {}

    public JobExpenseEscrow(Job job, User payer, BigDecimal budgetAmount, LocalDateTime heldAt) {
        if (job == null || payer == null || budgetAmount == null || budgetAmount.signum() <= 0 || heldAt == null) {
            throw new IllegalArgumentException("Expense escrow requires job, payer, positive budget and timestamp");
        }
        this.job = job;
        this.payer = payer;
        this.budgetAmount = budgetAmount;
        this.claimedAmount = BigDecimal.ZERO.setScale(2);
        this.reimbursedAmount = BigDecimal.ZERO.setScale(2);
        this.refundedAmount = BigDecimal.ZERO.setScale(2);
        this.status = JobExpenseEscrowStatus.HELD;
        this.heldAt = heldAt;
    }

    public void addClaim(BigDecimal amount) {
        assertHeld();
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Expense claim must be positive");
        }
        BigDecimal next = claimedAmount.add(amount);
        if (next.compareTo(budgetAmount) > 0) {
            throw new IllegalArgumentException("Expense claims exceed budget");
        }
        claimedAmount = next;
    }

    public void settle(BigDecimal reimbursed, BigDecimal refunded, LocalDateTime at) {
        assertHeld();
        if (reimbursed == null || refunded == null || at == null
                || reimbursed.signum() < 0 || refunded.signum() < 0
                || reimbursed.compareTo(claimedAmount) != 0
                || reimbursed.add(refunded).compareTo(budgetAmount) != 0) {
            throw new IllegalArgumentException("Expense settlement does not match held budget");
        }
        this.reimbursedAmount = reimbursed;
        this.refundedAmount = refunded;
        this.status = JobExpenseEscrowStatus.SETTLED;
        this.resolvedAt = at;
    }

    public void refundAll(LocalDateTime at) {
        assertHeld();
        if (at == null) throw new IllegalArgumentException("Resolution timestamp is required");
        this.reimbursedAmount = BigDecimal.ZERO.setScale(2);
        this.refundedAmount = budgetAmount;
        this.status = JobExpenseEscrowStatus.REFUNDED;
        this.resolvedAt = at;
    }

    private void assertHeld() {
        if (status != JobExpenseEscrowStatus.HELD) {
            throw new IllegalStateException("Expense escrow is already resolved");
        }
    }

    public Long getId() { return id; }
    public Job getJob() { return job; }
    public User getPayer() { return payer; }
    public BigDecimal getBudgetAmount() { return budgetAmount; }
    public BigDecimal getClaimedAmount() { return claimedAmount; }
    public BigDecimal getReimbursedAmount() { return reimbursedAmount; }
    public BigDecimal getRefundedAmount() { return refundedAmount; }
    public JobExpenseEscrowStatus getStatus() { return status; }
    public LocalDateTime getHeldAt() { return heldAt; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
}
