package com.doFast.dofastapp.config;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class WebSocketSessionRegistry {

    private final ConcurrentMap<String, SessionIdentity> sessions = new ConcurrentHashMap<>();

    public void register(String sessionId, String email, long authVersion) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("WebSocket session id is required");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("WebSocket session email is required");
        }
        if (authVersion < 0) {
            throw new IllegalArgumentException("WebSocket auth version cannot be negative");
        }
        sessions.put(sessionId, new SessionIdentity(email, authVersion));
    }

    public Optional<SessionIdentity> find(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(sessions.get(sessionId));
    }

    public void remove(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            sessions.remove(sessionId);
        }
    }

    @EventListener
    public void onSessionDisconnect(SessionDisconnectEvent event) {
        remove(event.getSessionId());
    }

    public record SessionIdentity(String email, long authVersion) {}
}
