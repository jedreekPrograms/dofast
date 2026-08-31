package com.doFast.dofastapp.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class PublicAuthRateLimitFilterTest {

    @Test
    void rejectsRequestsPastLimitWithRetryAfter() throws Exception {
        PublicAuthRateLimitFilter filter = new PublicAuthRateLimitFilter(
                2, 60, 100, false,
                Clock.fixed(Instant.parse("2026-08-31T03:00:00Z"), ZoneOffset.UTC)
        );

        assertThat(invoke(filter, "/users/login", "198.51.100.10", null).getStatus()).isEqualTo(200);
        assertThat(invoke(filter, "/users/login", "198.51.100.10", null).getStatus()).isEqualTo(200);

        MockHttpServletResponse rejected = invoke(filter, "/users/login", "198.51.100.10", null);
        assertThat(rejected.getStatus()).isEqualTo(429);
        assertThat(rejected.getHeader("Retry-After")).isEqualTo("60");
        assertThat(rejected.getContentAsString()).contains("Too Many Requests");
    }

    @Test
    void limitsPerEndpointAndClientAddress() throws Exception {
        PublicAuthRateLimitFilter filter = new PublicAuthRateLimitFilter(
                1, 60, 100, false,
                Clock.fixed(Instant.parse("2026-08-31T03:00:00Z"), ZoneOffset.UTC)
        );

        assertThat(invoke(filter, "/users/login", "198.51.100.10", null).getStatus()).isEqualTo(200);
        assertThat(invoke(filter, "/users/password/forgot", "198.51.100.10", null).getStatus()).isEqualTo(200);
        assertThat(invoke(filter, "/users/login", "198.51.100.11", null).getStatus()).isEqualTo(200);
        assertThat(invoke(filter, "/users/login", "198.51.100.10", null).getStatus()).isEqualTo(429);
    }

    @Test
    void ignoresForwardedForUnlessExplicitlyTrusted() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-08-31T03:00:00Z"), ZoneOffset.UTC);
        PublicAuthRateLimitFilter untrusted = new PublicAuthRateLimitFilter(1, 60, 100, false, clock);

        assertThat(invoke(untrusted, "/users/login", "10.0.0.5", "198.51.100.1").getStatus()).isEqualTo(200);
        assertThat(invoke(untrusted, "/users/login", "10.0.0.5", "198.51.100.2").getStatus()).isEqualTo(429);

        PublicAuthRateLimitFilter trusted = new PublicAuthRateLimitFilter(1, 60, 100, true, clock);
        assertThat(invoke(trusted, "/users/login", "10.0.0.5", "198.51.100.1").getStatus()).isEqualTo(200);
        assertThat(invoke(trusted, "/users/login", "10.0.0.5", "198.51.100.2").getStatus()).isEqualTo(200);
    }

    @Test
    void doesNotLimitNonAuthOrNonPostRequests() throws Exception {
        PublicAuthRateLimitFilter filter = new PublicAuthRateLimitFilter(
                1, 60, 100, false,
                Clock.fixed(Instant.parse("2026-08-31T03:00:00Z"), ZoneOffset.UTC)
        );

        MockHttpServletRequest get = new MockHttpServletRequest("GET", "/users/login");
        get.setRemoteAddr("198.51.100.10");
        MockHttpServletResponse getResponse = new MockHttpServletResponse();
        filter.doFilter(get, getResponse, new MockFilterChain());
        assertThat(getResponse.getStatus()).isEqualTo(200);

        assertThat(invoke(filter, "/jobs", "198.51.100.10", null).getStatus()).isEqualTo(200);
    }

    private static MockHttpServletResponse invoke(
            PublicAuthRateLimitFilter filter,
            String path,
            String remoteAddr,
            String forwardedFor
    ) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr(remoteAddr);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
