package com.doFast.dofastapp.config;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebSocketSessionRegistryTest {

    private static final Instant NOW = Instant.parse("2026-09-01T20:00:00Z");

    @Test
    void returnsCredentialBindingOnlyBeforeAccessTokenExpiry() {
        WebSocketSessionRegistry registry =
                new WebSocketSessionRegistry(Clock.fixed(NOW, ZoneOffset.UTC));
        registry.register("current", "user@example.com", 3L, NOW.plusSeconds(1));
        registry.register("expired", "user@example.com", 3L, NOW);

        assertTrue(registry.find("current").isPresent());
        assertFalse(registry.find("expired").isPresent());
        assertFalse(registry.find("expired").isPresent());
    }
}
