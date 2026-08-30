package com.doFast.dofastapp.user.auth.session;

public record AuthSessionCredentials(
        String refreshToken,
        String csrfToken
) {}
