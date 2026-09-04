package com.doFast.dofastapp.config;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryFixedWindowRateLimiterTest {

    private static final Instant NOW = Instant.parse("2026-09-04T12:00:00Z");

    @Test
    void accumulatesWeightedCostAndReportsRemainingWindow() {
        InMemoryFixedWindowRateLimiter limiter = new InMemoryFixedWindowRateLimiter(3, 60, 100);

        assertThat(limiter.register("account", 2, NOW).allowed()).isTrue();
        assertThat(limiter.register("account", 1, NOW.plusSeconds(10)).allowed()).isTrue();

        InMemoryFixedWindowRateLimiter.Decision rejected = limiter.register(
                "account",
                1,
                NOW.plusSeconds(25)
        );
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfterSeconds()).isEqualTo(35);
    }

    @Test
    void startsFreshWindowAfterExpiry() {
        InMemoryFixedWindowRateLimiter limiter = new InMemoryFixedWindowRateLimiter(1, 60, 100);

        assertThat(limiter.register("account", 1, NOW).allowed()).isTrue();
        assertThat(limiter.register("account", 1, NOW.plusSeconds(59)).allowed()).isFalse();
        assertThat(limiter.register("account", 1, NOW.plusSeconds(60)).allowed()).isTrue();
    }

    @Test
    void failsClosedAtCapacityButAdmitsNewKeyAfterExpiredEntriesAreCleaned() {
        InMemoryFixedWindowRateLimiter limiter = new InMemoryFixedWindowRateLimiter(1, 60, 100);
        for (int index = 0; index < 100; index++) {
            assertThat(limiter.register("account-" + index, 1, NOW).allowed()).isTrue();
        }

        InMemoryFixedWindowRateLimiter.Decision rejected = limiter.register("overflow", 1, NOW);
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfterSeconds()).isEqualTo(60);

        assertThat(limiter.register("replacement", 1, NOW.plusSeconds(60)).allowed()).isTrue();
    }

    @Test
    void rejectsInvalidConfigurationAndRegistrationInput() {
        assertThatThrownBy(() -> new InMemoryFixedWindowRateLimiter(0, 60, 100))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InMemoryFixedWindowRateLimiter(1, 0, 100))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InMemoryFixedWindowRateLimiter(1, 60, 99))
                .isInstanceOf(IllegalArgumentException.class);

        InMemoryFixedWindowRateLimiter limiter = new InMemoryFixedWindowRateLimiter(1, 60, 100);
        assertThatThrownBy(() -> limiter.register("", 1, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> limiter.register("account", 0, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> limiter.register("account", 1, null)).isInstanceOf(NullPointerException.class);
    }
}
