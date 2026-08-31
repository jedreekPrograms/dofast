package com.doFast.dofastapp.config;

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
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class WebSocketInboundRateLimitInterceptor implements ChannelInterceptor {

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong messagesSinceCleanup = new AtomicLong();
    private final Clock clock;
    private final int maxMessages;
    private final long windowSeconds;
    private final int maxEntries;

    public WebSocketInboundRateLimitInterceptor(
            @Value("${dofast.security.websocket-rate-limit.max-messages:120}") int maxMessages,
            @Value("${dofast.security.websocket-rate-limit.window-seconds:10}") long windowSeconds,
            @Value("${dofast.security.websocket-rate-limit.max-entries:10000}") int maxEntries
    ) {
        this(maxMessages, windowSeconds, maxEntries, Clock.systemUTC());
    }

    WebSocketInboundRateLimitInterceptor(int maxMessages, long windowSeconds, int maxEntries, Clock clock) {
        if (maxMessages < 1 || maxMessages > 10000
                || windowSeconds < 1 || windowSeconds > 3600
                || maxEntries < 100 || maxEntries > 1_000_000) {
            throw new IllegalArgumentException("Invalid websocket rate-limit configuration");
        }
        this.maxMessages = maxMessages;
        this.windowSeconds = windowSeconds;
        this.maxEntries = maxEntries;
        this.clock = clock;
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

        Decision decision = register(principal.getName().trim().toLowerCase(Locale.ROOT), clock.instant());
        if (!decision.allowed()) {
            throw new AccessDeniedException("WebSocket inbound rate limit exceeded; retry after "
                    + decision.retryAfterSeconds() + " seconds");
        }
        return message;
    }

    private Decision register(String key, Instant now) {
        if (messagesSinceCleanup.incrementAndGet() % 256 == 0) {
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

        if (window.count() <= maxMessages) {
            return new Decision(true, 0);
        }
        long retryAfter = Math.max(1, windowSeconds - (epochSecond - window.startedAtEpochSecond()));
        return new Decision(false, retryAfter);
    }

    private void cleanup(Instant now) {
        long cutoff = now.getEpochSecond() - windowSeconds;
        windows.entrySet().removeIf(entry -> entry.getValue().startedAtEpochSecond() <= cutoff);
    }

    private record Window(long startedAtEpochSecond, int count) {}
    private record Decision(boolean allowed, long retryAfterSeconds) {}
}
