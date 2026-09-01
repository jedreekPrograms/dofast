package com.doFast.dofastapp.config;

import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.enums.UserStatus;
import com.doFast.dofastapp.user.repository.UserRepository;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

@Component
public class WebSocketOutboundSecurityInterceptor implements ChannelInterceptor {

    private final UserRepository userRepository;
    private final WebSocketSessionRegistry sessionRegistry;

    public WebSocketOutboundSecurityInterceptor(
            UserRepository userRepository,
            WebSocketSessionRegistry sessionRegistry
    ) {
        this.userRepository = userRepository;
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        SimpMessageHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, SimpMessageHeaderAccessor.class);
        if (accessor == null || accessor.getMessageType() != SimpMessageType.MESSAGE) {
            return message;
        }

        String sessionId = accessor.getSessionId();
        WebSocketSessionRegistry.SessionIdentity identity = sessionRegistry.find(sessionId).orElse(null);
        if (identity == null) {
            return null;
        }

        User user = userRepository.findByEmailIgnoreCase(identity.email()).orElse(null);
        if (user == null
                || user.getStatus() != UserStatus.ACTIVE
                || user.getAuthVersion() != identity.authVersion()) {
            sessionRegistry.remove(sessionId);
            return null;
        }

        return message;
    }
}
