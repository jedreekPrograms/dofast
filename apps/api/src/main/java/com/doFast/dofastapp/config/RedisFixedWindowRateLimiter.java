package com.doFast.dofastapp.config;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

final class RedisFixedWindowRateLimiter implements FixedWindowRateLimiter {

    private static final Pattern SAFE_NAMESPACE = Pattern.compile("[a-z0-9-]{1,64}");
    private static final Pattern SAFE_PREFIX = Pattern.compile("[A-Za-z0-9:_-]{1,128}");
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final RedisScript<List> REGISTER_SCRIPT = new DefaultRedisScript<>("""
            local cost = tonumber(ARGV[1])
            local maximum = tonumber(ARGV[2])
            local window = tonumber(ARGV[3])
            local current = redis.call('INCRBY', KEYS[1], cost)
            if current == cost then
                redis.call('EXPIRE', KEYS[1], window)
            end
            local ttl = redis.call('TTL', KEYS[1])
            if ttl < 1 then
                redis.call('EXPIRE', KEYS[1], window)
                ttl = window
            end
            if current <= maximum then
                return {1, ttl}
            end
            return {0, ttl}
            """, List.class);

    private final StringRedisTemplate redisTemplate;
    private final String redisKeyPrefix;
    private final String namespace;
    private final int maxCostUnits;
    private final long windowSeconds;
    private final ThreadLocal<Mac> keyMac;

    RedisFixedWindowRateLimiter(
            StringRedisTemplate redisTemplate,
            String redisKeyPrefix,
            String namespace,
            int maxCostUnits,
            long windowSeconds,
            int maxEntries,
            String keyHmacSecret
    ) {
        if (!SAFE_PREFIX.matcher(Objects.requireNonNull(redisKeyPrefix, "redisKeyPrefix")).matches()
                || !SAFE_NAMESPACE.matcher(Objects.requireNonNull(namespace, "namespace")).matches()
                || maxCostUnits < 1 || windowSeconds < 1 || maxEntries < 100) {
            throw new IllegalArgumentException("Invalid Redis fixed-window rate-limit configuration");
        }
        byte[] hmacSecret = Objects.requireNonNull(keyHmacSecret, "keyHmacSecret")
                .getBytes(StandardCharsets.UTF_8);
        if (hmacSecret.length < 32) {
            throw new IllegalArgumentException("Rate-limit key HMAC secret must contain at least 32 bytes");
        }
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate");
        this.redisKeyPrefix = redisKeyPrefix;
        this.namespace = namespace;
        this.maxCostUnits = maxCostUnits;
        this.windowSeconds = windowSeconds;
        this.keyMac = ThreadLocal.withInitial(() -> createMac(hmacSecret));
    }

    @Override
    public Decision register(String key, int costUnits, Instant now) {
        if (key == null || key.isBlank() || costUnits < 1) {
            throw new IllegalArgumentException("Rate-limit key and cost must be present");
        }
        Objects.requireNonNull(now, "now");

        try {
            List<?> result = redisTemplate.execute(
                    REGISTER_SCRIPT,
                    List.of(redisKey(key)),
                    Integer.toString(costUnits),
                    Integer.toString(maxCostUnits),
                    Long.toString(windowSeconds)
            );
            if (result == null || result.size() != 2
                    || !(result.get(0) instanceof Number allowed)
                    || !(result.get(1) instanceof Number retryAfter)) {
                throw new RateLimitBackendUnavailableException("Redis returned an invalid rate-limit result");
            }
            return new Decision(allowed.longValue() == 1, Math.max(1, retryAfter.longValue()));
        } catch (DataAccessException exception) {
            throw new RateLimitBackendUnavailableException("Redis rate-limit backend is unavailable", exception);
        }
    }

    String redisKey(String key) {
        Mac mac = keyMac.get();
        mac.reset();
        byte[] digest = mac.doFinal((namespace + '\0' + key).getBytes(StandardCharsets.UTF_8));
        return redisKeyPrefix + ':' + namespace + ':' + HexFormat.of().formatHex(digest);
    }

    private static Mac createMac(byte[] secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return mac;
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not initialize rate-limit key HMAC", exception);
        }
    }
}
