package com.doFast.dofastapp.job.entity;

import com.doFast.dofastapp.common.enums.JobStatus;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "jobs",
        indexes = {
                @Index(name = "idx_jobs_status", columnList = "status"),
                @Index(name = "idx_jobs_created_by", columnList = "created_by_id"),
                @Index(name = "idx_jobs_taken_by", columnList = "taken_by_id"),
                @Index(name = "idx_jobs_status_created_at", columnList = "status,created_at")
        }
)
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    private int version;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, length = 4000)
    private String description;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private JobStatus status;

    @JdbcTypeCode(SqlTypes.GEOGRAPHY)
    @Column(name = "location", columnDefinition = "geography(Point,4326)")
    private Point location;

    @Column(name = "location_label", length = 120)
    private String locationLabel;

    @Column(name = "location_private_label", length = 200)
    private String locationPrivateLabel;

    @ManyToOne(optional = false)
    @JoinColumn(name = "created_by_id", nullable = false)
    private User createdBy;

    @ManyToOne
    @JoinColumn(name = "taken_by_id")
    private User takenBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "taken_at")
    private LocalDateTime takenAt;

    @Column(name = "completion_requested_at")
    private LocalDateTime completionRequestedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    public Job() {}

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void assignTo(User user, LocalDateTime at) {
        this.takenBy = user;
        this.takenAt = at;
        this.status = JobStatus.IN_PROGRESS;
    }

    public void requestCompletion(LocalDateTime at) {
        this.completionRequestedAt = at;
        this.status = JobStatus.COMPLETION_REQUESTED;
    }

    public void complete(LocalDateTime at) {
        this.completedAt = at;
        this.status = JobStatus.DONE;
    }

    public void cancel(LocalDateTime at) {
        this.cancelledAt = at;
        this.status = JobStatus.CANCELLED;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public BigDecimal getPrice() { return price; }
    public JobStatus getStatus() { return status; }
    public Point getLocation() { return location; }
    public String getLocationLabel() { return locationLabel; }
    public String getLocationPrivateLabel() { return locationPrivateLabel; }
    public User getCreatedBy() { return createdBy; }
    public User getTakenBy() { return takenBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public LocalDateTime getTakenAt() { return takenAt; }
    public LocalDateTime getCompletionRequestedAt() { return completionRequestedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }

    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public void setStatus(JobStatus status) { this.status = status; }
    public void setLocation(Point location) { this.location = location; }
    public void setLocationLabel(String locationLabel) { this.locationLabel = locationLabel; }
    public void setLocationPrivateLabel(String locationPrivateLabel) { this.locationPrivateLabel = locationPrivateLabel; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }
    public void setTakenBy(User takenBy) { this.takenBy = takenBy; }
}
