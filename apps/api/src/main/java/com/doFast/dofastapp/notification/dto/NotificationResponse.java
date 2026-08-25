package com.doFast.dofastapp.notification.dto;

import com.doFast.dofastapp.notification.enums.NotificationType;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        NotificationType type,
        String title,
        String body,
        Long jobId,
        Long disputeId,
        boolean read,
        LocalDateTime createdAt,
        LocalDateTime readAt
) {}
