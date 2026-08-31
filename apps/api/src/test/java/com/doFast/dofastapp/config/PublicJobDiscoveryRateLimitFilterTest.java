package com.doFast.dofastapp.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class PublicJobDiscoveryRateLimitFilterTest {

    @Test
    void rejectsRequestsPastLimitWithRetryAfter() throws Exception {
        PublicJobDiscoveryRateLimitFilter filter = new PublicJobDiscoveryRateLimitFilter(
                2, 60, 100, false,
                Clock.fixed(Instant.parse("2026-08-31T06:00:00Z"), ZoneOffset.UTC)
        );

        assertThat(invoke(filter, "/jobs/nearby", "198.51.100.10", null).getStatus()).isEqualTo(200);
        assertThat(invoke(filter, "/jobs/nearby", "198.51.100.10", null).getStatus()).isEqualTo(200);

        MockHttpServletResponse rejected = invoke(filter, "/jobs/nearby", "198.51.100.10", null);
        assertThat(rejected.getStatus()).isEqualTo(429);
        assertThat(rejected.getHeader("Retry-After")).isEqualTo("60");
        assertThat(rejected.getContentAsString()).contains("Too Many Requests");
    }

    @Test
    void isolatesBudgetPerEndpointAndClientAddress() throws Exception {
        PublicJobDiscoveryRateLimitFilter filter = new PublicJobDiscoveryRateLimitFilter(
                1, 60, 100, false,
                Clock.fixed(Instant.parse("2026-08-31T06:00:00Z"), ZoneOffset.UTC)
        );

        assertThat(invoke(filter, "/jobs", "198.51.100.10", null).getStatus()).isEqualTo(200);
        assertThat(invoke(filter, "/jobs/nearby", "198.51.100.10", null).getStatus()).isEqualTo(200);
        assertThat(invoke(filter, "/jobs", "198.51.100.11", null).getStatus()).isEqualTo(200);
        assertThat(invoke(filter, "/jobs", "198.51.100.10", null).getStatus()).isEqualTo(429);
    }

    @Test
    void ignoresForwardedForUnlessExplicitlyTrusted() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-08-31T06:00:00Z"), ZoneOffset.UTC);
        PublicJobDiscoveryRateLimitFilter untrusted = new PublicJobDiscoveryRateLimitFilter(1, 60, 100, false, clock);

        assertThat(invoke(untrusted, "/jobs", "10.0.0.5", "198.51.100.1").getStatus()).isEqualTo(200);
        assertThat(invoke(untrusted, "/jobs", "10.0.0.5", "198.51.100.2").getStatus()).isEqualTo(429);

        PublicJobDiscoveryRateLimitFilter trusted = new PublicJobDiscoveryRateLimitFilter(1, 60, 100, true, clock);
        assertThat(invoke(trusted, "/jobs", "10.0.0.5", "198.51.100.1").getStatus()).isEqualTo(200);
        assertThat(invoke(trusted, "/jobs", "10.0.0.5", "198.51.100.2").getStatus()).isEqualTo(200);
    }

    @Test
    void doesNotLimitOtherPathsOrMethods() throws Exception {
        PublicJobDiscoveryRateLimitFilter filter = new PublicJobDiscoveryRateLimitFilter(
                1, 60, 100, false,
                Clock.fixed(Instant.parse("2026-08-31T06:00:00Z"), ZoneOffset.UTC)
        );

        MockHttpServletRequest post = new MockHttpServletRequest("POST", "/jobs");
        post.setRemoteAddr("198.51.100.10");
        MockHttpServletResponse postResponse = new MockHttpServletResponse();
        filter.doFilter(post, postResponse, new MockFilterChain());
        assertThat(postResponse.getStatus()).isEqualTo(200);

        assertThat(invoke(filter, "/job-categories", "198.51.100.10", null).getStatus()).isEqualTo(200);
    }

    private static MockHttpServletResponse invoke(
            PublicJobDiscoveryRateLimitFilter filter,
            String path,
            String remoteAddr,
            String forwardedFor
    ) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRemoteAddr(remoteAddr);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
