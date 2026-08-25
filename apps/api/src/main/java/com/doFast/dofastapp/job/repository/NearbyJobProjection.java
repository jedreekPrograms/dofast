package com.doFast.dofastapp.job.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface NearbyJobProjection {

    Long getId();

    String getTitle();

    String getDescription();

    BigDecimal getPrice();

    String getStatus();

    String getLocationLabel();

    Double getDistanceMeters();

    LocalDateTime getCreatedAt();
}
