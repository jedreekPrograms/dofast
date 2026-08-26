package com.doFast.dofastapp.location.routing.dto;

import java.util.List;
import java.util.UUID;

public record RouteModeComparisonResponse(
        UUID quoteId,
        List<RouteModeEstimateResponse> estimates,
        boolean nonDrivingBetaWarningRequired
) {}
