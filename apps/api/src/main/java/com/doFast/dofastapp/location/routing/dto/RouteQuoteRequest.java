package com.doFast.dofastapp.location.routing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record RouteQuoteRequest(
        @NotNull @Valid RoutePointRequest origin,
        @NotNull @Valid RoutePointRequest destination
) {}
