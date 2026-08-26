package com.doFast.dofastapp.location.routing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record RouteQuoteRequest(
        @NotNull @Valid RoutePointRequest origin,
        @Size(max = 10) List<@NotNull @Valid RoutePointRequest> stops,
        @NotNull @Valid RoutePointRequest destination
) {
    public RouteQuoteRequest {
        stops = stops == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(stops));
    }
}
