package com.doFast.dofastapp.job.entity;

import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.job.category.JobCategory;
import com.doFast.dofastapp.location.routing.entity.RouteQuote;
import com.doFast.dofastapp.user.entity.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private JobCategory category;

    // Legacy column name retained deliberately: this is the origin/pickup point A.
    @JdbcTypeCode(SqlTypes.GEOGRAPHY)
    @Column(name = "location", columnDefinition = "geography(Point,4326)")
    private Point location;

    @Column(name = "location_label", length = 120)
    private String locationLabel;

    @Column(name = "location_private_label", length = 200)
    private String locationPrivateLabel;

    @JdbcTypeCode(SqlTypes.GEOGRAPHY)
    @Column(name = "destination_location", columnDefinition = "geography(Point,4326)")
    private Point destinationLocation;

    @Column(name = "destination_label", length = 120)
    private String destinationLabel;

    @Column(name = "destination_private_label", length = 200)
    private String destinationPrivateLabel;

    @OneToMany(mappedBy = "job", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequenceNo ASC")
    private List<JobRouteStop> routeStops = new ArrayList<>();

    @Column(name = "route_distance_meters")
    private Integer routeDistanceMeters;

    @Column(name = "route_duration_seconds")
    private Integer routeDurationSeconds;

    @Column(name = "route_encoded_polyline")
    private String routeEncodedPolyline;

    @Column(name = "route_provider", length = 32)
    private String routeProvider;

    @Column(name = "route_computed_at")
    private LocalDateTime routeComputedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_quote_id")
    private RouteQuote routeQuote;

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

    public void markDisputed() { this.status = JobStatus.DISPUTED; }

    public void restoreAfterDispute(JobStatus previousStatus) {
        if (previousStatus != JobStatus.IN_PROGRESS && previousStatus != JobStatus.COMPLETION_REQUESTED) {
            throw new IllegalArgumentException("Nieprawidłowy status do wznowienia zlecenia po sporze");
        }
        this.status = previousStatus;
    }

    public void complete(LocalDateTime at) {
        this.completedAt = at;
        this.status = JobStatus.DONE;
    }

    public void cancel(LocalDateTime at) {
        this.cancelledAt = at;
        this.status = JobStatus.CANCELLED;
    }

    public void addRouteStop(Point location, String publicLabel, String privateLabel, String placeId) {
        routeStops.add(new JobRouteStop(this, routeStops.size(), location, publicLabel, privateLabel, placeId));
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public BigDecimal getPrice() { return price; }
    public JobStatus getStatus() { return status; }
    public JobCategory getCategory() { return category; }
    public Point getLocation() { return location; }
    public String getLocationLabel() { return locationLabel; }
    public String getLocationPrivateLabel() { return locationPrivateLabel; }
    public Point getDestinationLocation() { return destinationLocation; }
    public String getDestinationLabel() { return destinationLabel; }
    public String getDestinationPrivateLabel() { return destinationPrivateLabel; }
    public List<JobRouteStop> getRouteStops() { return Collections.unmodifiableList(routeStops); }
    public Integer getRouteDistanceMeters() { return routeDistanceMeters; }
    public Integer getRouteDurationSeconds() { return routeDurationSeconds; }
    public String getRouteEncodedPolyline() { return routeEncodedPolyline; }
    public String getRouteProvider() { return routeProvider; }
    public LocalDateTime getRouteComputedAt() { return routeComputedAt; }
    public RouteQuote getRouteQuote() { return routeQuote; }
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
    public void setCategory(JobCategory category) { this.category = category; }
    public void setLocation(Point location) { this.location = location; }
    public void setLocationLabel(String locationLabel) { this.locationLabel = locationLabel; }
    public void setLocationPrivateLabel(String locationPrivateLabel) { this.locationPrivateLabel = locationPrivateLabel; }
    public void setDestinationLocation(Point destinationLocation) { this.destinationLocation = destinationLocation; }
    public void setDestinationLabel(String destinationLabel) { this.destinationLabel = destinationLabel; }
    public void setDestinationPrivateLabel(String destinationPrivateLabel) { this.destinationPrivateLabel = destinationPrivateLabel; }
    public void setRouteDistanceMeters(Integer routeDistanceMeters) { this.routeDistanceMeters = routeDistanceMeters; }
    public void setRouteDurationSeconds(Integer routeDurationSeconds) { this.routeDurationSeconds = routeDurationSeconds; }
    public void setRouteEncodedPolyline(String routeEncodedPolyline) { this.routeEncodedPolyline = routeEncodedPolyline; }
    public void setRouteProvider(String routeProvider) { this.routeProvider = routeProvider; }
    public void setRouteComputedAt(LocalDateTime routeComputedAt) { this.routeComputedAt = routeComputedAt; }
    public void setRouteQuote(RouteQuote routeQuote) { this.routeQuote = routeQuote; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }
    public void setTakenBy(User takenBy) { this.takenBy = takenBy; }
}
