package com.doFast.dofastapp.location.routing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

@Entity
@Table(name = "route_quote_stops")
public class RouteQuoteStop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "route_quote_id", nullable = false)
    private RouteQuote routeQuote;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @JdbcTypeCode(SqlTypes.GEOGRAPHY)
    @Column(name = "location", nullable = false, columnDefinition = "geography(Point,4326)")
    private Point location;

    @Column(name = "public_label", nullable = false, length = 120)
    private String publicLabel;

    @Column(name = "private_label", length = 200)
    private String privateLabel;

    @Column(name = "place_id", length = 255)
    private String placeId;

    protected RouteQuoteStop() {}

    public RouteQuoteStop(
            RouteQuote routeQuote,
            int sequenceNo,
            Point location,
            String publicLabel,
            String privateLabel,
            String placeId
    ) {
        this.routeQuote = routeQuote;
        this.sequenceNo = sequenceNo;
        this.location = location;
        this.publicLabel = publicLabel;
        this.privateLabel = privateLabel;
        this.placeId = placeId;
    }

    public int getSequenceNo() { return sequenceNo; }
    public Point getLocation() { return location; }
    public String getPublicLabel() { return publicLabel; }
    public String getPrivateLabel() { return privateLabel; }
    public String getPlaceId() { return placeId; }
}
