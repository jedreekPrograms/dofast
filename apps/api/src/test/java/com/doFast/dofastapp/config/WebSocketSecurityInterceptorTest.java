package com.doFast.dofastapp.config;

import com.doFast.dofastapp.common.util.JwtUtil;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.enums.UserStatus;
import com.doFast.dofastapp.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import java.security.Principal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebSocketSecurityInterceptorTest {

    @Test
    void acceptsConnectWhenAccessTokenAuthVersionMatchesUserAndBindsSession() {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        UserRepository userRepository = mock(UserRepository.class);
        WebSocketSessionRegistry sessionRegistry = new WebSocketSessionRegistry();
        User user = new User("user@example.com", "user");
        user.incrementAuthVersion();
        when(jwtUtil.parseAccessToken("current-token"))
                .thenReturn(new JwtUtil.AccessTokenIdentity(user.getEmail(), user.getAuthVersion()));
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        WebSocketSecurityInterceptor interceptor = new WebSocketSecurityInterceptor(
                jwtUtil, userRepository, null, null, sessionRegistry);

        Message<?> result = interceptor.preSend(connectMessage("current-token", "session-1"), null);
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(result, StompHeaderAccessor.class);

        assertNotNull(accessor);
        assertNotNull(accessor.getUser());
        assertEquals(user.getEmail(), accessor.getUser().getName());
        var identity = sessionRegistry.find("session-1").orElseThrow();
        assertEquals(user.getEmail(), identity.email());
        assertEquals(user.getAuthVersion(), identity.authVersion());
    }

    @Test
    void rejectsConnectWhenAccessTokenAuthVersionIsStale() {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        UserRepository userRepository = mock(UserRepository.class);
        WebSocketSessionRegistry sessionRegistry = new WebSocketSessionRegistry();
        User user = new User("user@example.com", "user");
        user.incrementAuthVersion();
        when(jwtUtil.parseAccessToken("stale-token"))
                .thenReturn(new JwtUtil.AccessTokenIdentity(user.getEmail(), user.getAuthVersion() - 1));
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        WebSocketSecurityInterceptor interceptor = new WebSocketSecurityInterceptor(
                jwtUtil, userRepository, null, null, sessionRegistry);

        assertThrows(
                BadCredentialsException.class,
                () -> interceptor.preSend(connectMessage("stale-token", "session-1"), null)
        );
        assertFalse(sessionRegistry.find("session-1").isPresent());
    }

    @Test
    void rejectsEstablishedSessionAfterAuthVersionChanges() {
        UserRepository userRepository = mock(UserRepository.class);
        WebSocketSessionRegistry sessionRegistry = new WebSocketSessionRegistry();
        User user = new User("user@example.com", "user");
        sessionRegistry.register("session-1", user.getEmail(), user.getAuthVersion());
        user.incrementAuthVersion();
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        WebSocketSecurityInterceptor interceptor = new WebSocketSecurityInterceptor(
                null, userRepository, null, null, sessionRegistry);

        assertThrows(
                BadCredentialsException.class,
                () -> interceptor.preSend(
                        message(StompCommand.SUBSCRIBE, user.getEmail(), "/user/queue/notifications", "session-1"),
                        null
                )
        );
        assertFalse(sessionRegistry.find("session-1").isPresent());
    }

    @Test
    void rejectsEstablishedSessionAfterAccountSuspension() {
        UserRepository userRepository = mock(UserRepository.class);
        WebSocketSessionRegistry sessionRegistry = new WebSocketSessionRegistry();
        User user = new User("user@example.com", "user");
        sessionRegistry.register("session-1", user.getEmail(), user.getAuthVersion());
        user.setStatus(UserStatus.SUSPENDED);
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        WebSocketSecurityInterceptor interceptor = new WebSocketSecurityInterceptor(
                null, userRepository, null, null, sessionRegistry);

        assertThrows(
                BadCredentialsException.class,
                () -> interceptor.preSend(
                        message(StompCommand.SUBSCRIBE, user.getEmail(), "/user/queue/notifications", "session-1"),
                        null
                )
        );
    }

    @Test
    void rejectsAuthenticatedClientSendBeforeBrokerDelivery() {
        UserRepository userRepository = mock(UserRepository.class);
        WebSocketSessionRegistry sessionRegistry = new WebSocketSessionRegistry();
        User user = new User("user@example.com", "user");
        sessionRegistry.register("session-1", user.getEmail(), user.getAuthVersion());
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        WebSocketSecurityInterceptor interceptor = new WebSocketSecurityInterceptor(
                null, userRepository, null, null, sessionRegistry);

        assertThrows(
                AccessDeniedException.class,
                () -> interceptor.preSend(
                        message(StompCommand.SEND, user.getEmail(), "/topic/tracking/42", "session-1"), null)
        );
        assertThrows(
                AccessDeniedException.class,
                () -> interceptor.preSend(
                        message(StompCommand.SEND, user.getEmail(), "/topic/chat/42", "session-1"), null)
        );
        assertThrows(
                AccessDeniedException.class,
                () -> interceptor.preSend(
                        message(StompCommand.SEND, user.getEmail(), "/queue/anything", "session-1"), null)
        );
        assertThrows(
                AccessDeniedException.class,
                () -> interceptor.preSend(
                        message(StompCommand.SEND, user.getEmail(), "/dofastapp/future-handler", "session-1"), null)
        );
    }

    @Test
    void unauthenticatedClientSendStillFailsAsAuthenticationError() {
        WebSocketSecurityInterceptor interceptor = new WebSocketSecurityInterceptor(null, null, null, null, null);

        assertThrows(
                BadCredentialsException.class,
                () -> interceptor.preSend(message(StompCommand.SEND, null, "/topic/tracking/42", "session-1"), null)
        );
    }

    private Message<byte[]> connectMessage(String token, String sessionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer " + token);
        accessor.setSessionId(sessionId);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> message(
            StompCommand command,
            String principalName,
            String destination,
            String sessionId
    ) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (principalName != null) {
            Principal principal = () -> principalName;
            accessor.setUser(principal);
        }
        accessor.setSessionId(sessionId);
        accessor.setDestination(destination);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
