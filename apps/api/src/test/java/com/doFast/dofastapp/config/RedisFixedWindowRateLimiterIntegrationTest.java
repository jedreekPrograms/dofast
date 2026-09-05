package com.doFast.dofastapp.config;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "REDIS_INTEGRATION_TEST", matches = "true")
class RedisFixedWindowRateLimiterIntegrationTest {

    private static final String SECRET = "integration-test-rate-limit-secret-32-bytes";
    private static final String PREFIX = "dofast:integration:" + UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-05T12:00:00Z");

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;

    @BeforeAll
    static void connect() {
        connectionFactory = new LettuceConnectionFactory("127.0.0.1", 6379);
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
    }

    @AfterAll
    static void disconnect() {
        if (redisTemplate != null) {
            Set<String> keys = redisTemplate.keys(PREFIX + ":*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        }
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void sharesOneAtomicBudgetAcrossLimiterInstancesAndIsolatesNamespaces() {
        RedisFixedWindowRateLimiter first = limiter("authenticated-operation", 3);
        RedisFixedWindowRateLimiter second = limiter("authenticated-operation", 3);
        RedisFixedWindowRateLimiter otherNamespace = limiter("authenticated-routing", 3);

        assertThat(first.register("account-42", 2, NOW).allowed()).isTrue();
        FixedWindowRateLimiter.Decision rejected = second.register("account-42", 2, NOW);
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfterSeconds()).isBetween(1L, 30L);
        assertThat(otherNamespace.register("account-42", 2, NOW).allowed()).isTrue();

        Set<String> keys = redisTemplate.keys(PREFIX + ":*");
        assertThat(keys).hasSize(2);
        assertThat(keys).allMatch(key -> !key.contains("account-42"));
    }

    @Test
    void admitsExactlyTheConfiguredNumberUnderConcurrentRequests() throws Exception {
        RedisFixedWindowRateLimiter first = limiter("websocket-inbound", 10);
        RedisFixedWindowRateLimiter second = limiter("websocket-inbound", 10);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Boolean>> attempts = new ArrayList<>();
            for (int index = 0; index < 40; index++) {
                RedisFixedWindowRateLimiter selected = index % 2 == 0 ? first : second;
                attempts.add(() -> selected.register("concurrent-account", 1, NOW).allowed());
            }

            List<Future<Boolean>> results = executor.invokeAll(attempts);
            long allowed = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    allowed++;
                }
            }
            assertThat(allowed).isEqualTo(10);
        } finally {
            executor.shutdownNow();
        }
    }

    private static RedisFixedWindowRateLimiter limiter(String namespace, int maximum) {
        return new RedisFixedWindowRateLimiter(
                redisTemplate,
                PREFIX,
                namespace,
                maximum,
                30,
                100,
                SECRET
        );
    }
}
