package com.doFast.dofastapp.payout.dto;

import java.time.LocalDateTime;

public record PayoutOnboardingStatusResponse(
        boolean available,
        boolean accountCreated,
        boolean detailsSubmitted,
        boolean payoutsEnabled,
        boolean transfersEnabled,
        boolean requirementsDue,
        boolean readyForPayout,
        LocalDateTime lastSyncedAt
) {}
