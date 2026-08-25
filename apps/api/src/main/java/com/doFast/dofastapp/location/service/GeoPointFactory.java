package com.doFast.dofastapp.location.service;

import com.doFast.dofastapp.location.dto.LocationRequest;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

public final class GeoPointFactory {

    public static final int WGS84_SRID = 4326;

    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), WGS84_SRID);

    private GeoPointFactory() {}

    public static Point from(LocationRequest location) {
        Point point = GEOMETRY_FACTORY.createPoint(
                new Coordinate(
                        location.getLongitude().doubleValue(),
                        location.getLatitude().doubleValue()
                )
        );
        point.setSRID(WGS84_SRID);
        return point;
    }
}
