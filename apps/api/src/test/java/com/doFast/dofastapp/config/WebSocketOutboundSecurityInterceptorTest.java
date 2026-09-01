package com.doFast.dofastapp.config;

import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.enums.UserStatus;
import com.doFast.dofastapp.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.MessageBuilder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSocketOutboundSecurityInterceptorTest {

    private static final Instant FUTURE_EXPIRY = Instant.parse("2099-01-01T00:00:00Z");

    @Test
    void allowsOutboundMessageForCurrentActiveSession() {
        UserRepository userRepository = mock(UserRepository.class);
        WebSocketSessionRegistry sessionRegistry = new WebSocketSessionRegistry();
        User user = new User("user@example.com", "user");
        sessionRegistry.register("session-1", user.getEmail(), user.getAuthVersion(), FUTURE_EXPIRY);
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        WebSocketOutboundSecurityInterceptor interceptor =
                new WebSocketOutboundSecurityInterceptor(userRepository, sessionRegistry);
        Message<byte[]> message = message(SimpMessageType.MESSAGE, "session-1");

        assertSame(message, interceptor.preSend(message, null));
        assertSame(user, userRepository.findByEmailIgnoreCase(user.getEmail()).orElseThrow());
    }

    @Test
    void dropsOutboundMessageAndForgetsSessionAfterAuthVersionChanges() {
        UserRepository userRepository = mock(UserRepository.class);
        WebSocketSessionRegistry sessionRegistry = new WebSocketSessionRegistry();
        User user = new User("user@example.com", "user");
        sessionRegistry.register("session-1", user.getEmail(), user.getAuthVersion(), FUTURE_EXPIRY);
        user.incrementAuthVersion();
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        WebSocketOutboundSecurityInterceptor interceptor =
                new WebSocketOutboundSecurityInterceptor(userRepository, sessionRegistry);

        assertNull(interceptor.preSend(message(SimpMessageType.MESSAGE, "session-1"), null));
        assertFalse(sessionRegistry.find("session-1").isPresent());
    }

    @Test
    void dropsOutboundMessageAndForgetsSuspendedSession() {
        UserRepository userRepository = mock(UserRepository.class);
        WebSocketSessionRegistry sessionRegistry = new WebSocketSessionRegistry();
        User user = new User("user@example.com", "user");
        sessionRegistry.register("session-1", user.getEmail(), user.getAuthVersion(), FUTURE_EXPIRY);
        user.setStatus(UserStatus.SUSPENDED);
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        WebSocketOutboundSecurityInterceptor interceptor =
                new WebSocketOutboundSecurityInterceptor(userRepository, sessionRegistry);

        assertNull(interceptor.preSend(message(SimpMessageType.MESSAGE, "session-1"), null));
        assertFalse(sessionRegistry.find("session-1").isPresent());
    }

    @Test
    void dropsMessageForUnregisteredSessionWithoutUserLookup() {
        UserRepository userRepository = mock(UserRepository.class);
        WebSocketSessionRegistry sessionRegistry = new WebSocketSessionRegistry();
        WebSocketOutboundSecurityInterceptor interceptor =
                new WebSocketOutboundSecurityInterceptor(userRepository, sessionRegistry);

        assertNull(interceptor.preSend(message(SimpMessageType.MESSAGE, "unknown-session"), null));
        verify(userRepository, never()).findByEmailIgnoreCase(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void dropsMessageAfterBoundAccessTokenExpiresWithoutUserLookup() {
        Instant now = Instant.parse("2026-09-01T20:00:00Z");
        UserRepository userRepository = mock(UserRepository.class);
        WebSocketSessionRegistry sessionRegistry =
                new WebSocketSessionRegistry(Clock.fixed(now, ZoneOffset.UTC));
        sessionRegistry.register("session-1", "user@example.com", 0L, now);
        WebSocketOutboundSecurityInterceptor interceptor =
                new WebSocketOutboundSecurityInterceptor(userRepository, sessionRegistry);

        assertNull(interceptor.preSend(message(SimpMessageType.MESSAGE, "session-1"), null));
        verify(userRepository, never()).findByEmailIgnoreCase(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void leavesNonMessageFramesUntouched() {
        UserRepository userRepository = mock(UserRepository.class);
        WebSocketSessionRegistry sessionRegistry = new WebSocketSessionRegistry();
        WebSocketOutboundSecurityInterceptor interceptor =
                new WebSocketOutboundSecurityInterceptor(userRepository, sessionRegistry);
        Message<byte[]> connected = message(SimpMessageType.CONNECT_ACK, "session-1");

        assertSame(connected, interceptor.preSend(connected, null));
        verify(userRepository, never()).findByEmailIgnoreCase(org.mockito.ArgumentMatchers.anyString());
    }

    private Message<byte[]> message(SimpMessageType type, String sessionId) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(type);
        accessor.setSessionId(sessionId);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
