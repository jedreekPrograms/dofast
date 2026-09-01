package com.doFast.dofastapp.config;

import com.doFast.dofastapp.chat.service.ChatAccessService;
import com.doFast.dofastapp.common.util.JwtUtil;
import com.doFast.dofastapp.location.tracking.service.LiveTrackingAccessService;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.enums.UserStatus;
import com.doFast.dofastapp.user.repository.UserRepository;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;

@Component
public class WebSocketSecurityInterceptor implements ChannelInterceptor {

    private static final String CHAT_TOPIC_PREFIX = "/topic/chat/";
    private static final String TRACKING_TOPIC_PREFIX = "/topic/tracking/";

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final ChatAccessService chatAccessService;
    private final LiveTrackingAccessService liveTrackingAccessService;
    private final WebSocketSessionRegistry sessionRegistry;

    public WebSocketSecurityInterceptor(
            JwtUtil jwtUtil,
            UserRepository userRepository,
            ChatAccessService chatAccessService,
            LiveTrackingAccessService liveTrackingAccessService,
            WebSocketSessionRegistry sessionRegistry
    ) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.chatAccessService = chatAccessService;
        this.liveTrackingAccessService = liveTrackingAccessService;
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        if (accessor.getCommand() == StompCommand.CONNECT) {
            authenticate(accessor);
            return message;
        }

        if (accessor.getCommand() == StompCommand.DISCONNECT) {
            sessionRegistry.remove(accessor.getSessionId());
            return message;
        }

        Principal principal = accessor.getUser();
        if (principal == null) {
            throw new BadCredentialsException("WebSocket session is not authenticated");
        }

        User user = requireCurrentSession(accessor, principal);

        // doFast currently exposes WebSocket only as a server-to-client delivery channel.
        // Allowing authenticated clients to SEND directly to /topic or /queue would let them
        // bypass REST ownership/business invariants and inject forged chat/tracking payloads
        // into the simple broker. If application message mappings are introduced later, they
        // must be explicitly allow-listed and authorized here before enabling client SEND.
        if (accessor.getCommand() == StompCommand.SEND) {
            throw new AccessDeniedException("Client WebSocket publishing is not allowed");
        }

        if (accessor.getCommand() == StompCommand.SUBSCRIBE) {
            authorizeSubscription(accessor, user);
        }

        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new BadCredentialsException("Missing websocket bearer token");
        }

        JwtUtil.AccessTokenIdentity identity;
        try {
            identity = jwtUtil.parseAccessToken(authorization.substring(7));
        } catch (Exception ex) {
            throw new BadCredentialsException("Invalid websocket bearer token", ex);
        }

        User user = activeUser(identity.email());
        if (user.getAuthVersion() != identity.authVersion()) {
            throw new BadCredentialsException("WebSocket access token is no longer valid");
        }

        String sessionId = requireSessionId(accessor);
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + user.getRole().name());
        accessor.setUser(new UsernamePasswordAuthenticationToken(user.getEmail(), null, List.of(authority)));
        sessionRegistry.register(sessionId, user.getEmail(), identity.authVersion());
    }

    private User requireCurrentSession(StompHeaderAccessor accessor, Principal principal) {
        String sessionId = requireSessionId(accessor);
        WebSocketSessionRegistry.SessionIdentity identity = sessionRegistry.find(sessionId)
                .orElseThrow(() -> new BadCredentialsException("WebSocket session identity is missing"));
        if (!identity.email().equalsIgnoreCase(principal.getName())) {
            sessionRegistry.remove(sessionId);
            throw new BadCredentialsException("WebSocket session identity does not match principal");
        }

        User user = activeUser(identity.email());
        if (user.getAuthVersion() != identity.authVersion()) {
            sessionRegistry.remove(sessionId);
            throw new BadCredentialsException("WebSocket session credentials are no longer valid");
        }
        return user;
    }

    private void authorizeSubscription(StompHeaderAccessor accessor, User user) {
        String destination = accessor.getDestination();
        if (destination == null) {
            throw new BadCredentialsException("Missing websocket destination");
        }

        if (destination.startsWith(CHAT_TOPIC_PREFIX)) {
            Long jobId = parseJobId(destination.substring(CHAT_TOPIC_PREFIX.length()), "chat");
            chatAccessService.requireParticipant(jobId, user);
            return;
        }

        if (destination.startsWith(TRACKING_TOPIC_PREFIX)) {
            Long jobId = parseJobId(destination.substring(TRACKING_TOPIC_PREFIX.length()), "tracking");
            liveTrackingAccessService.requireViewer(jobId, user);
            return;
        }

        if ("/user/queue/notifications".equals(destination)) {
            return;
        }

        throw new BadCredentialsException("WebSocket subscription is not allowed");
    }

    private User activeUser(String email) {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new BadCredentialsException("WebSocket user does not exist"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BadCredentialsException("WebSocket user is not active");
        }
        return user;
    }

    private String requireSessionId(StompHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            throw new BadCredentialsException("WebSocket session id is missing");
        }
        return sessionId;
    }

    private Long parseJobId(String value, String destinationType) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            throw new BadCredentialsException("Invalid " + destinationType + " destination", ex);
        }
    }
}
