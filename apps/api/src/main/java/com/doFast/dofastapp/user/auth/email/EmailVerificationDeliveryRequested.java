package com.doFast.dofastapp.user.auth.email;

public record EmailVerificationDeliveryRequested(
        Long userId,
        String recipientEmail,
        String rawToken
) {}
