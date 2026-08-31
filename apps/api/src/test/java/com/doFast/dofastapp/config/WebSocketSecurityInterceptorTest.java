package com.doFast.dofastapp.config;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertThrows;

class WebSocketSecurityInterceptorTest {

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
