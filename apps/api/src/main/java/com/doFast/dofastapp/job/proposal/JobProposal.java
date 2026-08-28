package com.doFast.dofastapp.job.proposal;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "job_proposals")
public class JobProposal {

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
    @JoinColumn(name = "proposer_id", nullable = false)
    private User proposer;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(length = 1000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobProposalStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

    protected JobProposal() {}

    public JobProposal(Job job, User proposer, BigDecimal amount, String message) {
        this.job = job;
        this.proposer = proposer;
        this.amount = amount;
        this.message = message;
        this.status = JobProposalStatus.SUBMITTED;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void accept(LocalDateTime at) {
        requireSubmitted();
        status = JobProposalStatus.ACCEPTED;
        acceptedAt = at;
    }

    public void reject() {
        if (status == JobProposalStatus.SUBMITTED) {
            status = JobProposalStatus.REJECTED;
        }
    }

    public void withdraw(LocalDateTime at) {
        requireSubmitted();
        status = JobProposalStatus.WITHDRAWN;
        withdrawnAt = at;
    }

    private void requireSubmitted() {
        if (status != JobProposalStatus.SUBMITTED) {
            throw new IllegalStateException("Proposal is not submitted");
        }
    }

    public Long getId() { return id; }
    public Job getJob() { return job; }
    public User getProposer() { return proposer; }
    public BigDecimal getAmount() { return amount; }
    public String getMessage() { return message; }
    public JobProposalStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public LocalDateTime getAcceptedAt() { return acceptedAt; }
    public LocalDateTime getWithdrawnAt() { return withdrawnAt; }
}
