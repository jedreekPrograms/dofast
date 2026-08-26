package com.doFast.dofastapp.user.auth.apple;

import java.time.Instant;
import java.util.UUID;

public record AppleLoginChallengeResponse(
        UUID challengeId,
        String state,
        String nonce,
        Instant expiresAt
) {}