package com.doFast.dofastapp.config;

import com.doFast.dofastapp.user.entity.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.Objects;
import java.util.regex.Pattern;

public class AuthenticatedRoutingRateLimitFilter extends OncePerRequestFilter {

    private static final String RATE_LIMIT_NAMESPACE = "authenticated-routing";
    private static final String CREATE_QUOTE_PATH = "/routing/quotes";
    private static final Pattern MODE_ESTIMATES_PATH = Pattern.compile(
            "^/routing/quotes/[^/]+/mode-estimates$"
    );
    private static final int CREATE_QUOTE_PROVIDER_CALLS = 1;
    private static final int MODE_ESTIMATES_PROVIDER_CALLS = 2;

    private final Clock clock;
    private final FixedWindowRateLimiter rateLimiter;

    public AuthenticatedRoutingRateLimitFilter(
            int maxProviderCalls,
            long windowSeconds,
            int maxEntries
    ) {
        this(maxProviderCalls, windowSeconds, maxEntries, Clock.systemUTC());
    }

    AuthenticatedRoutingRateLimitFilter(
            int maxProviderCalls,
            long windowSeconds,
            int maxEntries,
            Clock clock
    ) {
        validateConfiguration(maxProviderCalls, windowSeconds, maxEntries);
        this.rateLimiter = new InMemoryFixedWindowRateLimiter(maxProviderCalls, windowSeconds, maxEntries);
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    AuthenticatedRoutingRateLimitFilter(
            FixedWindowRateLimiterFactory rateLimiterFactory,
            int maxProviderCalls,
            long windowSeconds,
            int maxEntries
    ) {
        validateConfiguration(maxProviderCalls, windowSeconds, maxEntries);
        this.rateLimiter = Objects.requireNonNull(rateLimiterFactory, "rateLimiterFactory")
                .create(RATE_LIMIT_NAMESPACE, maxProviderCalls, windowSeconds, maxEntries);
        this.clock = Clock.systemUTC();
    }

    private static void validateConfiguration(int maxProviderCalls, long windowSeconds, int maxEntries) {
        if (maxProviderCalls < MODE_ESTIMATES_PROVIDER_CALLS || windowSeconds < 1 || maxEntries < 100) {
            throw new IllegalArgumentException("Invalid authenticated routing rate-limit configuration");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return providerCallCost(request) == 0;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof User user)) {
            filterChain.doFilter(request, response);
            return;
        }

        int providerCallCost = providerCallCost(request);
        String accountKey = user.getId() == null ? "authenticated-without-id" : user.getId().toString();
        FixedWindowRateLimiter.Decision decision;
        try {
            decision = rateLimiter.register(accountKey, providerCallCost, clock.instant());
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

    private int providerCallCost(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (HttpMethod.POST.matches(request.getMethod()) && CREATE_QUOTE_PATH.equals(path)) {
            return CREATE_QUOTE_PROVIDER_CALLS;
        }
        if (HttpMethod.GET.matches(request.getMethod()) && MODE_ESTIMATES_PATH.matcher(path).matches()) {
            return MODE_ESTIMATES_PROVIDER_CALLS;
        }
        return 0;
    }

}
