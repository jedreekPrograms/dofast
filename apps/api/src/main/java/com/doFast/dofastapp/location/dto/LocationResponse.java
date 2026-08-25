package com.doFast.dofastapp.location.dto;

public record LocationResponse(
        double latitude,
        double longitude,
        String label
) {}
