package com.doFast.dofastapp.config;

import com.doFast.dofastapp.user.entity.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.Objects;

public class AuthenticatedOperationRateLimitFilter extends OncePerRequestFilter {

    private static final String TRANSIENT_ACCOUNT_KEY = "authenticated-without-id";

    private final Clock clock;
    private final InMemoryFixedWindowRateLimiter rateLimiter;

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
        if (maxCostUnits < AuthenticatedOperationRateLimitPolicy.MAX_SINGLE_REQUEST_COST
                || windowSeconds < 1 || maxEntries < 100) {
            throw new IllegalArgumentException("Invalid authenticated operation rate-limit configuration");
        }
        this.rateLimiter = new InMemoryFixedWindowRateLimiter(maxCostUnits, windowSeconds, maxEntries);
        this.clock = Objects.requireNonNull(clock, "clock");
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
        InMemoryFixedWindowRateLimiter.Decision decision = rateLimiter.register(
                accountKey,
                costUnits(request),
                clock.instant()
        );
        if (!decision.allowed()) {
            response.setStatus(429);
            response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"status\":429,\"error\":\"Too Many Requests\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private int costUnits(HttpServletRequest request) {
        return AuthenticatedOperationRateLimitPolicy.costUnits(request.getMethod(), request.getRequestURI());
    }
}
