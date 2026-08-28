package com.doFast.dofastapp.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_service_areas")
public class UserServiceArea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @JdbcTypeCode(SqlTypes.GEOGRAPHY)
    @Column(name = "center_location", nullable = false, columnDefinition = "geography(Point,4326)")
    private Point centerLocation;

    @Column(name = "radius_meters", nullable = false)
    private Integer radiusMeters;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected UserServiceArea() {}

    public UserServiceArea(User user) {
        this.user = user;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public Point getCenterLocation() { return centerLocation; }
    public Integer getRadiusMeters() { return radiusMeters; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setCenterLocation(Point centerLocation) { this.centerLocation = centerLocation; }
    public void setRadiusMeters(Integer radiusMeters) { this.radiusMeters = radiusMeters; }
}
