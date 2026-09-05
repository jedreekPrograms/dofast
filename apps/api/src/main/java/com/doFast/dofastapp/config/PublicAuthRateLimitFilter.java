package com.doFast.dofastapp.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.Objects;
import java.util.Set;

public class PublicAuthRateLimitFilter extends OncePerRequestFilter {

    private static final String RATE_LIMIT_NAMESPACE = "public-auth";
    private static final Set<String> LIMITED_PATHS = Set.of(
            "/users",
            "/users/login",
            "/users/login/google",
            "/users/login/apple",
            "/users/login/apple/challenge",
            "/users/session/refresh",
            "/users/password/forgot",
            "/users/password/reset",
            "/users/email-verification/resend",
            "/users/email-verification/verify"
    );

    private final Clock clock;
    private final FixedWindowRateLimiter rateLimiter;
    private final boolean trustForwardedFor;

    public PublicAuthRateLimitFilter(int maxRequests, long windowSeconds, int maxEntries, boolean trustForwardedFor) {
        this(maxRequests, windowSeconds, maxEntries, trustForwardedFor, Clock.systemUTC());
    }

    PublicAuthRateLimitFilter(int maxRequests, long windowSeconds, int maxEntries, boolean trustForwardedFor, Clock clock) {
        validateConfiguration(maxRequests, windowSeconds, maxEntries);
        this.rateLimiter = new InMemoryFixedWindowRateLimiter(maxRequests, windowSeconds, maxEntries);
        this.trustForwardedFor = trustForwardedFor;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    PublicAuthRateLimitFilter(
            FixedWindowRateLimiterFactory rateLimiterFactory,
            int maxRequests,
            long windowSeconds,
            int maxEntries,
            boolean trustForwardedFor
    ) {
        validateConfiguration(maxRequests, windowSeconds, maxEntries);
        this.rateLimiter = Objects.requireNonNull(rateLimiterFactory, "rateLimiterFactory")
                .create(RATE_LIMIT_NAMESPACE, maxRequests, windowSeconds, maxEntries);
        this.trustForwardedFor = trustForwardedFor;
        this.clock = Clock.systemUTC();
    }

    private static void validateConfiguration(int maxRequests, long windowSeconds, int maxEntries) {
        if (maxRequests < 1 || windowSeconds < 1 || maxEntries < 100) {
            throw new IllegalArgumentException("Invalid public auth rate-limit configuration");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod()) || !LIMITED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String key = clientAddress(request) + "|" + request.getRequestURI();
        FixedWindowRateLimiter.Decision decision;
        try {
            decision = rateLimiter.register(key, 1, clock.instant());
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

    private String clientAddress(HttpServletRequest request) {
        if (trustForwardedFor) {
            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                String first = forwardedFor.split(",", 2)[0].trim();
                if (!first.isBlank() && first.length() <= 64) {
                    return first;
                }
            }
        }
        String remote = request.getRemoteAddr();
        return remote == null || remote.isBlank() ? "unknown" : remote;
    }
}
