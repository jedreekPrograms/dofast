package com.doFast.dofastapp.config;

import com.doFast.dofastapp.user.entity.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.Objects;

public class AuthenticatedOperationRateLimitFilter extends OncePerRequestFilter {

    private static final String RATE_LIMIT_NAMESPACE = "authenticated-operation";
    private static final String TRANSIENT_ACCOUNT_KEY = "authenticated-without-id";

    private final Clock clock;
    private final FixedWindowRateLimiter rateLimiter;

    public AuthenticatedOperationRateLimitFilter(
            int maxCostUnits,
            long windowSeconds,
            int maxEntries
    ) {
        this(maxCostUnits, windowSeconds, maxEntries, Clock.systemUTC());
    }

    AuthenticatedOperationRateLimitFilter(
            int maxCostUnits,
            long windowSeconds,
            int maxEntries,
            Clock clock
    ) {
        validateConfiguration(maxCostUnits, windowSeconds, maxEntries);
        this.rateLimiter = new InMemoryFixedWindowRateLimiter(maxCostUnits, windowSeconds, maxEntries);
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    AuthenticatedOperationRateLimitFilter(
            FixedWindowRateLimiterFactory rateLimiterFactory,
            int maxCostUnits,
            long windowSeconds,
            int maxEntries
    ) {
        validateConfiguration(maxCostUnits, windowSeconds, maxEntries);
        this.rateLimiter = Objects.requireNonNull(rateLimiterFactory, "rateLimiterFactory")
                .create(RATE_LIMIT_NAMESPACE, maxCostUnits, windowSeconds, maxEntries);
        this.clock = Clock.systemUTC();
    }

    AuthenticatedOperationRateLimitFilter(FixedWindowRateLimiter rateLimiter, Clock clock) {
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    private static void validateConfiguration(int maxCostUnits, long windowSeconds, int maxEntries) {
        if (maxCostUnits < AuthenticatedOperationRateLimitPolicy.MAX_SINGLE_REQUEST_COST
                || windowSeconds < 1 || maxEntries < 100) {
            throw new IllegalArgumentException("Invalid authenticated operation rate-limit configuration");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return costUnits(request) == 0;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof User user)) {
            filterChain.doFilter(request, response);
            return;
        }

        String accountKey = user.getId() == null ? TRANSIENT_ACCOUNT_KEY : user.getId().toString();
        FixedWindowRateLimiter.Decision decision;
        try {
            decision = rateLimiter.register(accountKey, costUnits(request), clock.instant());
        } catch (RateLimitBackendUnavailableException exception) {
            RateLimitHttpResponseWriter.writeBackendUnavailable(response);
            return;
        }
        if (!decision.allowed()) {
            RateLimitHttpResponseWriter.writeTooManyRequests(response, decision.retryAfterSeconds());
            return;
        }

        filterChain.doFilter(request, response);
    }

    private int costUnits(HttpServletRequest request) {
        return AuthenticatedOperationRateLimitPolicy.costUnits(request.getMethod(), request.getRequestURI());
    }
}
