package com.doFast.dofastapp.user.auth.session;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AuthSessionCleanupScheduler {

    private final AuthSessionService sessionService;

    public AuthSessionCleanupScheduler(AuthSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Scheduled(fixedDelayString = "${dofast.security.session.cleanup-interval-ms:3600000}")
    public void cleanup() {
        sessionService.cleanupOldSessions();
    }
}
