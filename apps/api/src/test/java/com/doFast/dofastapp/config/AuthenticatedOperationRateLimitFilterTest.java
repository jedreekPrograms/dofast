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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthenticatedOperationRateLimitFilterTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-09-04T12:00:00Z"),
            ZoneOffset.UTC
    );

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void sharesWeightedBudgetAcrossFinancialAndOrdinaryMutations() throws Exception {
        AuthenticatedOperationRateLimitFilter filter = filter();
        authenticate(10L);

        assertThat(invoke(filter, "POST", "/payments/create-intent").getStatus()).isEqualTo(200);

        MockHttpServletResponse rejected = invoke(filter, "PUT", "/saved-jobs/42");
        assertThat(rejected.getStatus()).isEqualTo(429);
        assertThat(rejected.getHeader("Retry-After")).isEqualTo("60");
        assertThat(rejected.getContentAsString()).contains("Too Many Requests");
    }

    @Test
    void chargesEveryAuthenticatedMutationAcrossEndpointFamilies() throws Exception {
        AuthenticatedOperationRateLimitFilter filter = filter();
        authenticate(10L);

        for (int index = 0; index < 10; index++) {
            assertThat(invoke(filter, "PUT", "/saved-jobs/" + index).getStatus()).isEqualTo(200);
            assertThat(invoke(filter, "DELETE", "/user-blocks/" + index).getStatus()).isEqualTo(200);
        }

        assertThat(invoke(filter, "PATCH", "/users/me/profile").getStatus()).isEqualTo(429);
    }

    @Test
    void chargesExpensiveReadsAndSharesTheirBudget() throws Exception {
        AuthenticatedOperationRateLimitFilter filter = filter();
        authenticate(10L);

        assertThat(invoke(filter, "GET", "/jobs/recommended").getStatus()).isEqualTo(200);
        assertThat(invoke(filter, "GET", "/wallet/transactions").getStatus()).isEqualTo(200);
        assertThat(invoke(filter, "GET", "/chat/jobs/3/messages").getStatus()).isEqualTo(200);
        assertThat(invoke(filter, "GET", "/admin/finance/reconciliation").getStatus()).isEqualTo(200);

        assertThat(invoke(filter, "POST", "/disputes").getStatus()).isEqualTo(429);
    }

    @Test
    void isolatesBudgetPerAuthenticatedAccount() throws Exception {
        AuthenticatedOperationRateLimitFilter filter = filter();

        authenticate(10L);
        assertThat(invoke(filter, "POST", "/wallet/payouts").getStatus()).isEqualTo(200);
        assertThat(invoke(filter, "POST", "/disputes").getStatus()).isEqualTo(429);

        authenticate(11L);
        assertThat(invoke(filter, "POST", "/wallet/payouts").getStatus()).isEqualTo(200);
    }

    @Test
    void doesNotChargeAnonymousAllowlistOrOrdinaryReads() throws Exception {
        AuthenticatedOperationRateLimitFilter filter = filter();
        authenticate(10L);

        for (int index = 0; index < 30; index++) {
            assertThat(invoke(filter, "POST", "/users/login").getStatus()).isEqualTo(200);
            assertThat(invoke(filter, "GET", "/users/me").getStatus()).isEqualTo(200);
        }

        assertThat(invoke(filter, "POST", "/payments/create-intent").getStatus()).isEqualTo(200);
    }

    @Test
    void leavesUnauthenticatedRequestsForSecurityChainToReject() throws Exception {
        AuthenticatedOperationRateLimitFilter filter = filter();

        assertThat(invoke(filter, "POST", "/payments/create-intent").getStatus()).isEqualTo(200);
        assertThat(invoke(filter, "POST", "/payments/create-intent").getStatus()).isEqualTo(200);
    }

    @Test
    void principalsWithoutPersistentIdShareFailClosedBudget() throws Exception {
        AuthenticatedOperationRateLimitFilter filter = filter();
        authenticate(null);

        assertThat(invoke(filter, "POST", "/payments/create-intent").getStatus()).isEqualTo(200);
        assertThat(invoke(filter, "POST", "/disputes").getStatus()).isEqualTo(429);
    }

    @Test
    void failsClosedWithoutEnteringControllerWhenSharedBackendIsUnavailable() throws Exception {
        FixedWindowRateLimiter unavailable = (key, costUnits, now) -> {
            throw new RateLimitBackendUnavailableException("Redis unavailable");
        };
        AuthenticatedOperationRateLimitFilter filter =
                new AuthenticatedOperationRateLimitFilter(unavailable, FIXED_CLOCK);
        authenticate(10L);

        MockHttpServletResponse response = invoke(filter, "POST", "/payments/create-intent");

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getHeader("Retry-After")).isEqualTo("1");
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString()).contains("Service Unavailable");
    }

    @Test
    void rejectsConfigurationBelowLargestWeightOrStorageBounds() {
        assertThatThrownBy(() -> new AuthenticatedOperationRateLimitFilter(19, 60, 100, FIXED_CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuthenticatedOperationRateLimitFilter(20, 0, 100, FIXED_CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuthenticatedOperationRateLimitFilter(20, 60, 99, FIXED_CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static AuthenticatedOperationRateLimitFilter filter() {
        return new AuthenticatedOperationRateLimitFilter(20, 60, 100, FIXED_CLOCK);
    }

    private static void authenticate(Long userId) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of())
        );
    }

    private static MockHttpServletResponse invoke(
            AuthenticatedOperationRateLimitFilter filter,
            String method,
            String path
    ) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
