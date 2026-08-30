package com.doFast.dofastapp.user.auth.session;

import com.doFast.dofastapp.user.dto.AuthResponse;

public record AuthSessionGrant(
        AuthResponse response,
        String refreshToken,
        String csrfToken
) {}
