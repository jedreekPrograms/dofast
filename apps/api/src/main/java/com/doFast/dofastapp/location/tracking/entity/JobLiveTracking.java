package com.doFast.dofastapp.location.tracking.entity;

import com.doFast.dofastapp.location.routing.provider.RouteProviderResult;
import com.doFast.dofastapp.location.tracking.enums.TrackingPhase;
import com.doFast.dofastapp.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

import java.time.Instant;

@Entity
@Table(name = "job_live_tracking")
public class JobLiveTracking {

    @Id
    @Column(name = "job_id")
    private Long jobId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "worker_id", nullable = false)
    private User worker;

    @Version
    @Column(nullable = false)
    private int version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TrackingPhase phase;

    @JdbcTypeCode(SqlTypes.GEOGRAPHY)
    @Column(name = "current_location", columnDefinition = "geography(Point,4326)")
    private Point currentLocation;

    @Column(name = "accuracy_meters")
    private Double accuracyMeters;

    @Column(name = "heading_degrees")
    private Double headingDegrees;

    @Column(name = "speed_meters_per_second")
    private Double speedMetersPerSecond;

    @Column(name = "captured_at")
    private Instant capturedAt;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(name = "sharing_started_at", nullable = false)
    private Instant sharingStartedAt;

    @Column(name = "sharing_stopped_at")
    private Instant sharingStoppedAt;

    @Column(name = "remaining_distance_meters")
    private Integer remainingDistanceMeters;

    @Column(name = "remaining_duration_seconds")
    private Integer remainingDurationSeconds;

    @Column(name = "remaining_encoded_polyline")
    private String remainingEncodedPolyline;

    @Column(name = "remaining_provider", length = 32)
    private String remainingProvider;

    @Column(name = "remaining_computed_at")
    private Instant remainingComputedAt;

    @JdbcTypeCode(SqlTypes.GEOGRAPHY)
    @Column(name = "eta_origin_location", columnDefinition = "geography(Point,4326)")
    private Point etaOriginLocation;

    public JobLiveTracking() {}

    public static JobLiveTracking start(Long jobId, User worker, Instant now) {
        JobLiveTracking tracking = new JobLiveTracking();
        tracking.jobId = jobId;
        tracking.worker = worker;
        tracking.phase = TrackingPhase.TO_ORIGIN;
        tracking.sharingStartedAt = now;
        return tracking;
    }

    public void updatePosition(
            Point location,
            Double accuracyMeters,
            Double headingDegrees,
            Double speedMetersPerSecond,
            Instant capturedAt,
            Instant receivedAt
    ) {
        this.currentLocation = location;
        this.accuracyMeters = accuracyMeters;
        this.headingDegrees = headingDegrees;
        this.speedMetersPerSecond = speedMetersPerSecond;
        this.capturedAt = capturedAt;
        this.receivedAt = receivedAt;
        this.sharingStoppedAt = null;
    }

    public void switchToDestination() {
        this.phase = TrackingPhase.TO_DESTINATION;
        clearEstimate();
    }

    public void applyEstimate(RouteProviderResult estimate, Point etaOrigin, Instant computedAt) {
        this.remainingDistanceMeters = estimate.distanceMeters();
        this.remainingDurationSeconds = estimate.durationSeconds();
        this.remainingEncodedPolyline = estimate.encodedPolyline();
        this.remainingProvider = estimate.provider();
        this.remainingComputedAt = computedAt;
        this.etaOriginLocation = etaOrigin;
    }

    public void stopAndClear(Instant now) {
        currentLocation = null;
        accuracyMeters = null;
        headingDegrees = null;
        speedMetersPerSecond = null;
        capturedAt = null;
        receivedAt = now;
        sharingStoppedAt = now;
        clearEstimate();
    }

    private void clearEstimate() {
        remainingDistanceMeters = null;
        remainingDurationSeconds = null;
        remainingEncodedPolyline = null;
        remainingProvider = null;
        remainingComputedAt = null;
        etaOriginLocation = null;
    }

    public Long getJobId() { return jobId; }
    public User getWorker() { return worker; }
    public TrackingPhase getPhase() { return phase; }
    public Point getCurrentLocation() { return currentLocation; }
    public Double getAccuracyMeters() { return accuracyMeters; }
    public Double getHeadingDegrees() { return headingDegrees; }
    public Double getSpeedMetersPerSecond() { return speedMetersPerSecond; }
    public Instant getCapturedAt() { return capturedAt; }
    public Instant getReceivedAt() { return receivedAt; }
    public Instant getSharingStartedAt() { return sharingStartedAt; }
    public Instant getSharingStoppedAt() { return sharingStoppedAt; }
    public Integer getRemainingDistanceMeters() { return remainingDistanceMeters; }
    public Integer getRemainingDurationSeconds() { return remainingDurationSeconds; }
    public String getRemainingEncodedPolyline() { return remainingEncodedPolyline; }
    public String getRemainingProvider() { return remainingProvider; }
    public Instant getRemainingComputedAt() { return remainingComputedAt; }
    public Point getEtaOriginLocation() { return etaOriginLocation; }
}
