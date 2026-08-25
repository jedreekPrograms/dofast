package com.doFast.dofastapp.location.service;

import com.doFast.dofastapp.location.dto.LocationRequest;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GeoPointFactoryTest {

    @Test
    void createsWgs84PointWithLongitudeAsXAndLatitudeAsY() {
        LocationRequest request = new LocationRequest();
        request.setLatitude(new BigDecimal("51.1128"));
        request.setLongitude(new BigDecimal("17.0601"));
        request.setPublicLabel("Wrocław, Plac Grunwaldzki");

        Point point = GeoPointFactory.from(request);

        assertEquals(GeoPointFactory.WGS84_SRID, point.getSRID());
        assertEquals(17.0601, point.getX(), 0.000001);
        assertEquals(51.1128, point.getY(), 0.000001);
    }
}
