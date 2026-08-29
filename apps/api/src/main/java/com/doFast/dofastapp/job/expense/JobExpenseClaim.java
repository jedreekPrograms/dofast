package com.doFast.dofastapp.job.expense;

import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.attachment.JobAttachment;
import com.doFast.dofastapp.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "job_expense_claims")
public class JobExpenseClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    private int version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "worker_id", nullable = false)
    private User worker;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attachment_id", nullable = false, unique = true)
    private JobAttachment attachment;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected JobExpenseClaim() {}

    public JobExpenseClaim(Job job, User worker, JobAttachment attachment, BigDecimal amount, LocalDateTime createdAt) {
        if (job == null || worker == null || attachment == null || amount == null || amount.signum() <= 0 || createdAt == null) {
            throw new IllegalArgumentException("Expense claim requires job, worker, receipt, positive amount and timestamp");
        }
        this.job = job;
        this.worker = worker;
        this.attachment = attachment;
        this.amount = amount;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Job getJob() { return job; }
    public User getWorker() { return worker; }
    public JobAttachment getAttachment() { return attachment; }
    public BigDecimal getAmount() { return amount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
