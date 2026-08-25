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

import java.math.BigDecimal;

@Entity
@Table(
        name = "escrow_transactions",
        uniqueConstraints = @UniqueConstraint(name = "uk_escrow_transactions_job", columnNames = "job_id"),
        indexes = {
                @Index(name = "idx_escrow_transactions_payer", columnList = "payer_id"),
                @Index(name = "idx_escrow_transactions_payee", columnList = "payee_id")
        }
)
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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

    public Transaction() {}

    public Long getId() { return id; }
    public Job getJob() { return job; }
    public User getPayer() { return payer; }
    public User getPayee() { return payee; }
    public BigDecimal getAmount() { return amount; }
    public TransactionStatus getStatus() { return status; }

    public void setJob(Job job) { this.job = job; }
    public void setPayer(User payer) { this.payer = payer; }
    public void setPayee(User payee) { this.payee = payee; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public void setStatus(TransactionStatus status) { this.status = status; }
}
