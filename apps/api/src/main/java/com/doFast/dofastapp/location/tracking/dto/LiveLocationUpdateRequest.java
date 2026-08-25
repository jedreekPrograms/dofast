package com.doFast.dofastapp.location.tracking.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public record LiveLocationUpdateRequest(
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
        @DecimalMin("0.0") @DecimalMax("10000.0") Double accuracyMeters,
        @DecimalMin("0.0") @DecimalMax("360.0") Double headingDegrees,
        @DecimalMin("0.0") @DecimalMax("120.0") Double speedMetersPerSecond,
        @NotNull Instant capturedAt
) {}
