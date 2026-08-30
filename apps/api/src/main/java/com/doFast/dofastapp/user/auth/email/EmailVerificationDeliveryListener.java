package com.doFast.dofastapp.user.auth.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class EmailVerificationDeliveryListener {
    private static final Logger log = LoggerFactory.getLogger(EmailVerificationDeliveryListener.class);
    private final EmailVerificationMailer mailer;

    public EmailVerificationDeliveryListener(EmailVerificationMailer mailer) {
        this.mailer = mailer;
    }

    @Async("passwordRecoveryExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void deliver(EmailVerificationDeliveryRequested event) {
        try {
            mailer.sendVerificationLink(event.recipientEmail(), event.rawToken());
        } catch (RuntimeException deliveryFailure) {
            log.warn("Email verification delivery failed for user id {}", event.userId(), deliveryFailure);
        }
    }
}
