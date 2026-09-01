package com.doFast.dofastapp.config;

import com.doFast.dofastapp.common.util.JwtUtil;
import com.doFast.dofastapp.user.entity.User;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebSocketSecurityInterceptorTest {

    @Test
    void acceptsConnectWhenAccessTokenAuthVersionMatchesUser() {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        UserRepository userRepository = mock(UserRepository.class);
        User user = new User("user@example.com", "user");
        user.incrementAuthVersion();
        when(jwtUtil.parseAccessToken("current-token"))
                .thenReturn(new JwtUtil.AccessTokenIdentity(user.getEmail(), user.getAuthVersion()));
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        WebSocketSecurityInterceptor interceptor = new WebSocketSecurityInterceptor(
                jwtUtil, userRepository, null, null);

        Message<?> result = interceptor.preSend(connectMessage("current-token"), null);
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(result, StompHeaderAccessor.class);

        assertNotNull(accessor);
        assertNotNull(accessor.getUser());
        assertEquals(user.getEmail(), accessor.getUser().getName());
    }

    @Test
    void rejectsConnectWhenAccessTokenAuthVersionIsStale() {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        UserRepository userRepository = mock(UserRepository.class);
        User user = new User("user@example.com", "user");
        user.incrementAuthVersion();
        when(jwtUtil.parseAccessToken("stale-token"))
                .thenReturn(new JwtUtil.AccessTokenIdentity(user.getEmail(), user.getAuthVersion() - 1));
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        WebSocketSecurityInterceptor interceptor = new WebSocketSecurityInterceptor(
                jwtUtil, userRepository, null, null);

        assertThrows(
                BadCredentialsException.class,
                () -> interceptor.preSend(connectMessage("stale-token"), null)
        );
    }

    @Test
    void rejectsAuthenticatedClientSendBeforeBrokerDelivery() {
        WebSocketSecurityInterceptor interceptor = new WebSocketSecurityInterceptor(null, null, null, null);

        assertThrows(
                AccessDeniedException.class,
                () -> interceptor.preSend(message(StompCommand.SEND, "worker@example.com", "/topic/tracking/42"), null)
        );
        assertThrows(
                AccessDeniedException.class,
                () -> interceptor.preSend(message(StompCommand.SEND, "owner@example.com", "/topic/chat/42"), null)
        );
        assertThrows(
                AccessDeniedException.class,
                () -> interceptor.preSend(message(StompCommand.SEND, "user@example.com", "/queue/anything"), null)
        );
        assertThrows(
                AccessDeniedException.class,
                () -> interceptor.preSend(message(StompCommand.SEND, "user@example.com", "/dofastapp/future-handler"), null)
        );
    }

    @Test
    void unauthenticatedClientSendStillFailsAsAuthenticationError() {
        WebSocketSecurityInterceptor interceptor = new WebSocketSecurityInterceptor(null, null, null, null);

        assertThrows(
                BadCredentialsException.class,
                () -> interceptor.preSend(message(StompCommand.SEND, null, "/topic/tracking/42"), null)
        );
    }

    private Message<byte[]> connectMessage(String token) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer " + token);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> message(StompCommand command, String principalName, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (principalName != null) {
            Principal principal = () -> principalName;
            accessor.setUser(principal);
        }
        accessor.setDestination(destination);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
