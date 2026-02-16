package com.doFast.dofastapp.job.entity;

import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.user.entity.User;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    private String description;

    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    private JobStatus status;

    @ManyToOne
    private User createdBy;

    public Job() {}

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public JobStatus getStatus() {
        return status;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public void setStatus(JobStatus status) {
        this.status = status;
    }

    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
    }
}
