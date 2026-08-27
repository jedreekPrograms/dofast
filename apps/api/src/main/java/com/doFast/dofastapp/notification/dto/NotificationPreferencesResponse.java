package com.doFast.dofastapp.notification.dto;

import com.doFast.dofastapp.notification.enums.NotificationType;

import java.util.Set;

public record NotificationPreferencesResponse(
        Set<NotificationType> mutableTypes,
        Set<NotificationType> mutedTypes
) {}
