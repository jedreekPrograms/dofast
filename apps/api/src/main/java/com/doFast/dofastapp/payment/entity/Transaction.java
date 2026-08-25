package com.doFast.dofastapp.payment.entity;

import com.doFast.dofastapp.common.enums.TransactionStatus;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.user.entity.User;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    private Job job;

    @ManyToOne
    private User payer;

    @ManyToOne
    private User payee;

    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
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
