package com.doFast.dofastapp.location.routing.dto;

import java.math.BigDecimal;

public record RoutePointResponse(
        BigDecimal latitude,
        BigDecimal longitude,
        String publicLabel,
        String privateLabel,
        String placeId
) {}
