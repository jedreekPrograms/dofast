package com.doFast.dofastapp.user.dto;

import java.time.LocalDateTime;

public record UserServiceAreaResponse(
        boolean configured,
        Double latitude,
        Double longitude,
        Integer radiusKm,
        LocalDateTime updatedAt
) {
    public static UserServiceAreaResponse notConfigured() {
        return new UserServiceAreaResponse(false, null, null, null, null);
    }
}
