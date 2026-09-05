package com.doFast.dofastapp.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RateLimitBackendConfigurationTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void selectsMemoryWithoutRedisAndRejectsUnknownBackend() {
        ObjectProvider<StringRedisTemplate> provider = provider(null);
        RateLimitBackendConfiguration configuration = new RateLimitBackendConfiguration();

        FixedWindowRateLimiterFactory factory = configuration.fixedWindowRateLimiterFactory(
                " MEMORY ", "dofast:test", "", provider
        );

        assertThat(factory.create("public-auth", 10, 60, 100))
                .isInstanceOf(InMemoryFixedWindowRateLimiter.class);
        assertThatThrownBy(() -> configuration.fixedWindowRateLimiterFactory(
                "database", "dofast:test", SECRET, provider
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported rate-limit backend");
    }

    @Test
    void redisRequiresTemplateAndStrongKeySecret() {
        RateLimitBackendConfiguration configuration = new RateLimitBackendConfiguration();

        assertThatThrownBy(() -> configuration.fixedWindowRateLimiterFactory(
                "redis", "dofast:test", SECRET, provider(null)
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("StringRedisTemplate");

        FixedWindowRateLimiterFactory factory = configuration.fixedWindowRateLimiterFactory(
                "redis", "dofast:test", "short", provider(mock(StringRedisTemplate.class))
        );
        assertThatThrownBy(() -> factory.create("public-auth", 10, 60, 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HMAC secret");
    }

    @Test
    void everyApplicationLimiterUsesAnIsolatedSharedBackendNamespace() {
        List<String> namespaces = new ArrayList<>();
        FixedWindowRateLimiterFactory recordingFactory = (namespace, maximum, window, capacity) -> {
            namespaces.add(namespace);
            return (key, cost, now) -> new FixedWindowRateLimiter.Decision(true, 0);
        };

        new PublicAuthRateLimitFilter(recordingFactory, 30, 60, 100, false);
        new PublicJobDiscoveryRateLimitFilter(recordingFactory, 120, 60, 100, false);
        new AuthenticatedRoutingRateLimitFilter(recordingFactory, 60, 60, 100);
        new AuthenticatedOperationRateLimitFilter(recordingFactory, 240, 60, 100);
        new WebSocketInboundRateLimitInterceptor(recordingFactory, 120, 10, 100);

        assertThat(namespaces).containsExactly(
                "public-auth",
                "public-job-discovery",
                "authenticated-routing",
                "authenticated-operation",
                "websocket-inbound"
        );
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<StringRedisTemplate> provider(StringRedisTemplate template) {
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(template);
        return provider;
    }
}
