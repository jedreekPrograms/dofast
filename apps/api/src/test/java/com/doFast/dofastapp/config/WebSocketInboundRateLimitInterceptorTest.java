package com.doFast.dofastapp.config;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;

import java.security.Principal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebSocketInboundRateLimitInterceptorTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-08-31T04:00:00Z"), ZoneOffset.UTC);

    @Test
    void rejectsMessagesAbovePerPrincipalWindow() {
        WebSocketInboundRateLimitInterceptor interceptor =
                new WebSocketInboundRateLimitInterceptor(2, 10, 100, FIXED_CLOCK);

        assertDoesNotThrow(() -> interceptor.preSend(message(StompCommand.SEND, "user@example.com"), null));
        assertDoesNotThrow(() -> interceptor.preSend(message(StompCommand.SUBSCRIBE, "USER@example.com"), null));

        assertThrows(AccessDeniedException.class,
                () -> interceptor.preSend(message(StompCommand.SEND, "user@example.com"), null));
    }

    @Test
    void isolatesDifferentAuthenticatedPrincipals() {
        WebSocketInboundRateLimitInterceptor interceptor =
                new WebSocketInboundRateLimitInterceptor(1, 10, 100, FIXED_CLOCK);

        assertDoesNotThrow(() -> interceptor.preSend(message(StompCommand.SEND, "one@example.com"), null));
        assertThrows(AccessDeniedException.class,
                () -> interceptor.preSend(message(StompCommand.SEND, "one@example.com"), null));
        assertDoesNotThrow(() -> interceptor.preSend(message(StompCommand.SEND, "two@example.com"), null));
    }

    @Test
    void connectAndDisconnectAreNotChargedAgainstMessageBudget() {
        WebSocketInboundRateLimitInterceptor interceptor =
                new WebSocketInboundRateLimitInterceptor(1, 10, 100, FIXED_CLOCK);

        assertDoesNotThrow(() -> interceptor.preSend(message(StompCommand.CONNECT, "user@example.com"), null));
        assertDoesNotThrow(() -> interceptor.preSend(message(StompCommand.DISCONNECT, "user@example.com"), null));
        assertDoesNotThrow(() -> interceptor.preSend(message(StompCommand.SEND, "user@example.com"), null));
        assertThrows(AccessDeniedException.class,
                () -> interceptor.preSend(message(StompCommand.SEND, "user@example.com"), null));
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class,
                () -> new WebSocketInboundRateLimitInterceptor(0, 10, 100, FIXED_CLOCK));
        assertThrows(IllegalArgumentException.class,
                () -> new WebSocketInboundRateLimitInterceptor(10, 0, 100, FIXED_CLOCK));
        assertThrows(IllegalArgumentException.class,
                () -> new WebSocketInboundRateLimitInterceptor(10, 10, 99, FIXED_CLOCK));
    }

    private Message<byte[]> message(StompCommand command, String principalName) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        Principal principal = () -> principalName;
        accessor.setUser(principal);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
