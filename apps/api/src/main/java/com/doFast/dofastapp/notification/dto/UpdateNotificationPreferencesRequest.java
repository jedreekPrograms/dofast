package com.doFast.dofastapp.notification.dto;

import com.doFast.dofastapp.notification.enums.NotificationType;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record UpdateNotificationPreferencesRequest(
        @NotNull Set<NotificationType> mutedTypes
) {}
