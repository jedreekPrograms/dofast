package com.doFast.dofastapp.location.routing.entity;

import com.doFast.dofastapp.location.routing.provider.RouteProviderResult;
import com.doFast.dofastapp.user.entity.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "route_quotes")
public class RouteQuote {

    @Id
    private UUID id;

    @Version
    @Column(nullable = false)
    private int version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @JdbcTypeCode(SqlTypes.GEOGRAPHY)
    @Column(name = "origin", nullable = false, columnDefinition = "geography(Point,4326)")
    private Point origin;

    @Column(name = "origin_public_label", nullable = false, length = 120)
    private String originPublicLabel;

    @Column(name = "origin_private_label", length = 200)
    private String originPrivateLabel;

    @Column(name = "origin_place_id", length = 255)
    private String originPlaceId;

    @JdbcTypeCode(SqlTypes.GEOGRAPHY)
    @Column(name = "destination", nullable = false, columnDefinition = "geography(Point,4326)")
    private Point destination;

    @Column(name = "destination_public_label", nullable = false, length = 120)
    private String destinationPublicLabel;

    @Column(name = "destination_private_label", length = 200)
    private String destinationPrivateLabel;

    @Column(name = "destination_place_id", length = 255)
    private String destinationPlaceId;

    @OneToMany(mappedBy = "routeQuote", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequenceNo ASC")
    private List<RouteQuoteStop> stops = new ArrayList<>();

    @Column(name = "distance_meters", nullable = false)
    private int distanceMeters;

    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds;

    @Column(name = "encoded_polyline")
    private String encodedPolyline;

    @Column(name = "provider", nullable = false, length = 32)
    private String provider;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;

    public RouteQuote() {}

    public void initialize(
            UUID id,
            User user,
            Point origin,
            String originPublicLabel,
            String originPrivateLabel,
            String originPlaceId,
            Point destination,
            String destinationPublicLabel,
            String destinationPrivateLabel,
            String destinationPlaceId,
            RouteProviderResult route,
            LocalDateTime createdAt,
            LocalDateTime expiresAt
    ) {
        this.id = id;
        this.user = user;
        this.origin = origin;
        this.originPublicLabel = originPublicLabel;
        this.originPrivateLabel = originPrivateLabel;
        this.originPlaceId = originPlaceId;
        this.destination = destination;
        this.destinationPublicLabel = destinationPublicLabel;
        this.destinationPrivateLabel = destinationPrivateLabel;
        this.destinationPlaceId = destinationPlaceId;
        this.distanceMeters = route.distanceMeters();
        this.durationSeconds = route.durationSeconds();
        this.encodedPolyline = route.encodedPolyline();
        this.provider = route.provider();
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public void addStop(Point location, String publicLabel, String privateLabel, String placeId) {
        stops.add(new RouteQuoteStop(this, stops.size(), location, publicLabel, privateLabel, placeId));
    }

    public void markConsumed(LocalDateTime at) {
        this.consumedAt = at;
    }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public Point getOrigin() { return origin; }
    public String getOriginPublicLabel() { return originPublicLabel; }
    public String getOriginPrivateLabel() { return originPrivateLabel; }
    public String getOriginPlaceId() { return originPlaceId; }
    public Point getDestination() { return destination; }
    public String getDestinationPublicLabel() { return destinationPublicLabel; }
    public String getDestinationPrivateLabel() { return destinationPrivateLabel; }
    public String getDestinationPlaceId() { return destinationPlaceId; }
    public List<RouteQuoteStop> getStops() { return Collections.unmodifiableList(stops); }
    public int getDistanceMeters() { return distanceMeters; }
    public int getDurationSeconds() { return durationSeconds; }
    public String getEncodedPolyline() { return encodedPolyline; }
    public String getProvider() { return provider; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public LocalDateTime getConsumedAt() { return consumedAt; }
}
