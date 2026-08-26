package com.doFast.dofastapp.user.auth.apple;

public record AppleIdentity(
        String subject,
        String email,
        String displayName,
        boolean privateRelayEmail
) {}