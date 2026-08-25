package com.doFast.dofastapp.user.dto;

public record AdminOverviewResponse(
        long totalUsers,
        long activeUsers,
        long suspendedUsers
) {}
