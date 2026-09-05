package com.doFast.dofastapp.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.time.Clock;
import java.util.Locale;
import java.util.Objects;

@Component
public class WebSocketInboundRateLimitInterceptor implements ChannelInterceptor {

    private static final String RATE_LIMIT_NAMESPACE = "websocket-inbound";

    private final Clock clock;
    private final FixedWindowRateLimiter rateLimiter;

    @Autowired
    public WebSocketInboundRateLimitInterceptor(
            FixedWindowRateLimiterFactory rateLimiterFactory,
            @Value("${dofast.security.websocket-rate-limit.max-messages:120}") int maxMessages,
            @Value("${dofast.security.websocket-rate-limit.window-seconds:10}") long windowSeconds,
            @Value("${dofast.security.websocket-rate-limit.max-entries:10000}") int maxEntries
    ) {
        validateConfiguration(maxMessages, windowSeconds, maxEntries);
        this.rateLimiter = Objects.requireNonNull(rateLimiterFactory, "rateLimiterFactory")
                .create(RATE_LIMIT_NAMESPACE, maxMessages, windowSeconds, maxEntries);
        this.clock = Clock.systemUTC();
    }

    WebSocketInboundRateLimitInterceptor(int maxMessages, long windowSeconds, int maxEntries, Clock clock) {
        validateConfiguration(maxMessages, windowSeconds, maxEntries);
        this.rateLimiter = new InMemoryFixedWindowRateLimiter(maxMessages, windowSeconds, maxEntries);
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    WebSocketInboundRateLimitInterceptor(FixedWindowRateLimiter rateLimiter, Clock clock) {
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    private static void validateConfiguration(int maxMessages, long windowSeconds, int maxEntries) {
        if (maxMessages < 1 || maxMessages > 10000
                || windowSeconds < 1 || windowSeconds > 3600
                || maxEntries < 100 || maxEntries > 1_000_000) {
            throw new IllegalArgumentException("Invalid websocket rate-limit configuration");
        }
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        if (command == StompCommand.CONNECT || command == StompCommand.DISCONNECT) {
            return message;
        }

        Principal principal = accessor.getUser();
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            return message;
        }

        FixedWindowRateLimiter.Decision decision;
        try {
            decision = rateLimiter.register(
                    principal.getName().trim().toLowerCase(Locale.ROOT),
                    1,
                    clock.instant()
            );
        } catch (RateLimitBackendUnavailableException exception) {
            throw new AccessDeniedException("WebSocket inbound rate-limit backend unavailable", exception);
        }
        if (!decision.allowed()) {
            throw new AccessDeniedException("WebSocket inbound rate limit exceeded; retry after "
                    + decision.retryAfterSeconds() + " seconds");
        }
        return message;
    }
}
