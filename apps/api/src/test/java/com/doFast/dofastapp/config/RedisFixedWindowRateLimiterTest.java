package com.doFast.dofastapp.config;

import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisFixedWindowRateLimiterTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final Instant NOW = Instant.parse("2026-09-05T12:00:00Z");

    @Test
    void hashesRawIdentityAndSeparatesNamespaces() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        RedisFixedWindowRateLimiter auth = limiter(template, "public-auth");
        RedisFixedWindowRateLimiter jobs = limiter(template, "public-job-discovery");

        String authKey = auth.redisKey("203.0.113.4|/users/login");

        assertThat(authKey)
                .startsWith("dofast:test:rate-limit:v1:public-auth:")
                .doesNotContain("203.0.113.4")
                .doesNotContain("users/login")
                .hasSize("dofast:test:rate-limit:v1:public-auth:".length() + 64);
        assertThat(auth.redisKey("203.0.113.4|/users/login")).isEqualTo(authKey);
        assertThat(jobs.redisKey("203.0.113.4|/users/login")).isNotEqualTo(authKey);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void mapsAtomicScriptResultToDecisionAndServerTtl() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        when(template.execute(any(), anyList(), any(Object[].class)))
                .thenReturn(List.of(0L, 17L));

        FixedWindowRateLimiter.Decision decision = limiter(template, "authenticated-operation")
                .register("42", 20, NOW);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.retryAfterSeconds()).isEqualTo(17);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void translatesRedisOutageToBackendUnavailable() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        when(template.execute(any(), anyList(), any(Object[].class)))
                .thenThrow(new QueryTimeoutException("timeout"));

        assertThatThrownBy(() -> limiter(template, "authenticated-operation").register("42", 1, NOW))
                .isInstanceOf(RateLimitBackendUnavailableException.class)
                .hasCauseInstanceOf(QueryTimeoutException.class);
    }

    @Test
    void rejectsUnsafeNamesAndWeakSecrets() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);

        assertThatThrownBy(() -> new RedisFixedWindowRateLimiter(
                template, "unsafe prefix", "public-auth", 10, 60, 100, SECRET
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RedisFixedWindowRateLimiter(
                template, "dofast:test", "PUBLIC_AUTH", 10, 60, 100, SECRET
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RedisFixedWindowRateLimiter(
                template, "dofast:test", "public-auth", 10, 60, 100, "too-short"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private static RedisFixedWindowRateLimiter limiter(StringRedisTemplate template, String namespace) {
        return new RedisFixedWindowRateLimiter(
                template,
                "dofast:test:rate-limit:v1",
                namespace,
                20,
                60,
                100,
                SECRET
        );
    }
}
