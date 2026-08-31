package com.doFast.dofastapp.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class PublicAuthRateLimitFilter extends OncePerRequestFilter {

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

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong requestsSinceCleanup = new AtomicLong();
    private final Clock clock;
    private final int maxRequests;
    private final long windowSeconds;
    private final int maxEntries;
    private final boolean trustForwardedFor;

    public PublicAuthRateLimitFilter(int maxRequests, long windowSeconds, int maxEntries, boolean trustForwardedFor) {
        this(maxRequests, windowSeconds, maxEntries, trustForwardedFor, Clock.systemUTC());
    }

    PublicAuthRateLimitFilter(int maxRequests, long windowSeconds, int maxEntries, boolean trustForwardedFor, Clock clock) {
        if (maxRequests < 1 || windowSeconds < 1 || maxEntries < 100) {
            throw new IllegalArgumentException("Invalid public auth rate-limit configuration");
        }
        this.maxRequests = maxRequests;
        this.windowSeconds = windowSeconds;
        this.maxEntries = maxEntries;
        this.trustForwardedFor = trustForwardedFor;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod()) || !LIMITED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Instant now = clock.instant();
        String key = clientAddress(request) + "|" + request.getRequestURI();
        Decision decision = register(key, now);

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

    private Decision register(String key, Instant now) {
        if (requestsSinceCleanup.incrementAndGet() % 256 == 0) {
            cleanup(now);
        }
        if (!windows.containsKey(key) && windows.size() >= maxEntries) {
            cleanup(now);
            if (windows.size() >= maxEntries) {
                return new Decision(false, windowSeconds);
            }
        }

        long epochSecond = now.getEpochSecond();
        Window window = windows.compute(key, (ignored, current) -> {
            if (current == null || epochSecond - current.startedAtEpochSecond() >= windowSeconds) {
                return new Window(epochSecond, 1);
            }
            return new Window(current.startedAtEpochSecond(), current.count() + 1);
        });

        if (window.count() <= maxRequests) {
            return new Decision(true, 0);
        }
        long retryAfter = Math.max(1, windowSeconds - (epochSecond - window.startedAtEpochSecond()));
        return new Decision(false, retryAfter);
    }

    private void cleanup(Instant now) {
        long cutoff = now.getEpochSecond() - windowSeconds;
        windows.entrySet().removeIf(entry -> entry.getValue().startedAtEpochSecond() <= cutoff);
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

    private record Window(long startedAtEpochSecond, int count) {}
    private record Decision(boolean allowed, long retryAfterSeconds) {}
}
