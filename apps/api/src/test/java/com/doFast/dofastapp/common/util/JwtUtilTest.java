package com.doFast.dofastapp.common.util;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtUtilTest {

    @Test
    void parsedAccessIdentityCarriesCredentialVersionAndExpiration() {
        JwtUtil jwtUtil = new JwtUtil("test-only-jwt-secret-with-at-least-32-bytes", 60_000L);
        Instant issuedAfter = Instant.now().minusSeconds(1);

        JwtUtil.AccessTokenIdentity identity = jwtUtil.parseAccessToken(
                jwtUtil.generateToken("user@example.com", 7L)
        );

        assertEquals("user@example.com", identity.email());
        assertEquals(7L, identity.authVersion());
        assertTrue(identity.expiresAt().isAfter(issuedAfter.plusSeconds(59)));
        assertTrue(identity.expiresAt().isBefore(Instant.now().plusSeconds(61)));
    }
}
