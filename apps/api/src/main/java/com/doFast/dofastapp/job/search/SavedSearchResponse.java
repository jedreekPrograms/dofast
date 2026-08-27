package com.doFast.dofastapp.job.search;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SavedSearchResponse(
        Long id,
        String name,
        String query,
        String categorySlug,
        String categoryName,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Double latitude,
        Double longitude,
        Integer radiusKm,
        boolean alertsEnabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
