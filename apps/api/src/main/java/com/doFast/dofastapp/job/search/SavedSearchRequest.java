package com.doFast.dofastapp.job.search;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record SavedSearchRequest(
        @NotBlank @Size(max = 80) String name,
        @Size(max = 100) String query,
        @Size(max = 80) String categorySlug,
        @DecimalMin(value = "0.00") BigDecimal minPrice,
        @DecimalMin(value = "0.00") BigDecimal maxPrice,
        Double latitude,
        Double longitude,
        @Min(1) @Max(100) Integer radiusKm,
        boolean alertsEnabled
) {}
