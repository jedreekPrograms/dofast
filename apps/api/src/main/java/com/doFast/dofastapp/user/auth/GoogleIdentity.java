package com.doFast.dofastapp.user.auth;

public record GoogleIdentity(
        String subject,
        String email,
        String displayName,
        boolean authoritativeForEmail
) {}