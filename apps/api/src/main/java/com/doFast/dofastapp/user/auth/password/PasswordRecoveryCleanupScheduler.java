package com.doFast.dofastapp.user.auth.password;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PasswordRecoveryCleanupScheduler {

    private final PasswordRecoveryService passwordRecoveryService;

    public PasswordRecoveryCleanupScheduler(PasswordRecoveryService passwordRecoveryService) {
        this.passwordRecoveryService = passwordRecoveryService;
    }

    @Scheduled(fixedDelayString = "${dofast.security.password-recovery.cleanup-interval-ms:3600000}")
    public void cleanup() {
        passwordRecoveryService.cleanupOldTokens();
    }
}
