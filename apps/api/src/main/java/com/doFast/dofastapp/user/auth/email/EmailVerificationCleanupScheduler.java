package com.doFast.dofastapp.user.auth.email;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EmailVerificationCleanupScheduler {
    private final EmailVerificationService service;

    public EmailVerificationCleanupScheduler(EmailVerificationService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${dofast.security.email-verification.cleanup-interval-ms:3600000}")
    public void cleanup() {
        service.cleanupOldTokens();
    }
}
