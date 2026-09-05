package com.doFast.dofastapp.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Locale;

@Configuration(proxyBeanMethods = false)
class RateLimitBackendConfiguration {

    @Bean
    FixedWindowRateLimiterFactory fixedWindowRateLimiterFactory(
            @Value("${dofast.security.rate-limit.backend:memory}") String backend,
            @Value("${dofast.security.rate-limit.redis-key-prefix:dofast:rate-limit:v1}") String redisKeyPrefix,
            @Value("${dofast.security.rate-limit.key-hmac-secret:}") String keyHmacSecret,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider
    ) {
        String normalizedBackend = backend == null ? "" : backend.trim().toLowerCase(Locale.ROOT);
        return switch (normalizedBackend) {
            case "memory" -> (namespace, maxCostUnits, windowSeconds, maxEntries) ->
                    new InMemoryFixedWindowRateLimiter(maxCostUnits, windowSeconds, maxEntries);
            case "redis" -> {
                StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
                if (redisTemplate == null) {
                    throw new IllegalStateException("Redis rate-limit backend requires StringRedisTemplate");
                }
                yield (namespace, maxCostUnits, windowSeconds, maxEntries) ->
                        new RedisFixedWindowRateLimiter(
                                redisTemplate,
                                redisKeyPrefix,
                                namespace,
                                maxCostUnits,
                                windowSeconds,
                                maxEntries,
                                keyHmacSecret
                        );
            }
            default -> throw new IllegalArgumentException("Unsupported rate-limit backend: " + backend);
        };
    }
}
