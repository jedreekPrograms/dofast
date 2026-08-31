package com.doFast.dofastapp.config;

import com.doFast.dofastapp.user.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthenticatedRoutingRateLimitFilterTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-31T08:00:00Z"),
            ZoneOffset.UTC
    );

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void chargesProviderCallsAcrossRoutingEndpointsAndReturnsRetryAfter() throws Exception {
        AuthenticatedRoutingRateLimitFilter filter = new AuthenticatedRoutingRateLimitFilter(3, 60, 100, FIXED_CLOCK);
        authenticate(10L);

        assertThat(invoke(filter, "POST", "/routing/quotes").getStatus()).isEqualTo(200);
        assertThat(invoke(filter, "GET", "/routing/quotes/123/mode-estimates").getStatus()).isEqualTo(200);

        MockHttpServletResponse rejected = invoke(filter, "POST", "/routing/quotes");
        assertThat(rejected.getStatus()).isEqualTo(429);
        assertThat(rejected.getHeader("Retry-After")).isEqualTo("60");
        assertThat(rejected.getContentAsString()).contains("Too Many Requests");
    }

    @Test
    void isolatesBudgetPerAuthenticatedAccount() throws Exception {
        AuthenticatedRoutingRateLimitFilter filter = new AuthenticatedRoutingRateLimitFilter(2, 60, 100, FIXED_CLOCK);

        authenticate(10L);
        assertThat(invoke(filter, "GET", "/routing/quotes/a/mode-estimates").getStatus()).isEqualTo(200);
        assertThat(invoke(filter, "POST", "/routing/quotes").getStatus()).isEqualTo(429);

        authenticate(11L);
        assertThat(invoke(filter, "GET", "/routing/quotes/b/mode-estimates").getStatus()).isEqualTo(200);
    }

    @Test
    void doesNotChargeCachedQuoteReadsOrOtherRequests() throws Exception {
        AuthenticatedRoutingRateLimitFilter filter = new AuthenticatedRoutingRateLimitFilter(2, 60, 100, FIXED_CLOCK);
        authenticate(10L);

        assertThat(invoke(filter, "GET", "/routing/quotes/123").getStatus()).isEqualTo(200);
        assertThat(invoke(filter, "GET", "/jobs").getStatus()).isEqualTo(200);
        assertThat(invoke(filter, "POST", "/routing/quotes").getStatus()).isEqualTo(200);
        assertThat(invoke(filter, "POST", "/routing/quotes").getStatus()).isEqualTo(200);
    }

    @Test
    void leavesUnauthenticatedRequestsForSecurityChainToReject() throws Exception {
        AuthenticatedRoutingRateLimitFilter filter = new AuthenticatedRoutingRateLimitFilter(2, 60, 100, FIXED_CLOCK);

        assertThat(invoke(filter, "POST", "/routing/quotes").getStatus()).isEqualTo(200);
        assertThat(invoke(filter, "POST", "/routing/quotes").getStatus()).isEqualTo(200);
        assertThat(invoke(filter, "POST", "/routing/quotes").getStatus()).isEqualTo(200);
    }

    @Test
    void authenticatedPrincipalWithoutPersistentIdSharesFailClosedBudget() throws Exception {
        AuthenticatedRoutingRateLimitFilter filter = new AuthenticatedRoutingRateLimitFilter(2, 60, 100, FIXED_CLOCK);
        authenticate(null);

        assertThat(invoke(filter, "GET", "/routing/quotes/a/mode-estimates").getStatus()).isEqualTo(200);
        assertThat(invoke(filter, "POST", "/routing/quotes").getStatus()).isEqualTo(429);
    }

    @Test
    void rejectsInvalidConfiguration() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> new AuthenticatedRoutingRateLimitFilter(1, 60, 100, FIXED_CLOCK)
        ).isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> new AuthenticatedRoutingRateLimitFilter(2, 0, 100, FIXED_CLOCK)
        ).isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> new AuthenticatedRoutingRateLimitFilter(2, 60, 99, FIXED_CLOCK)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    private static void authenticate(Long userId) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of())
        );
    }

    private static MockHttpServletResponse invoke(
            AuthenticatedRoutingRateLimitFilter filter,
            String method,
            String path
    ) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
