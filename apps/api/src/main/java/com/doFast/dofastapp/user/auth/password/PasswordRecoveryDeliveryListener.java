package com.doFast.dofastapp.user.auth.password;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PasswordRecoveryDeliveryListener {

    private static final Logger log = LoggerFactory.getLogger(PasswordRecoveryDeliveryListener.class);

    private final PasswordRecoveryMailer mailer;

    public PasswordRecoveryDeliveryListener(PasswordRecoveryMailer mailer) {
        this.mailer = mailer;
    }

    @Async("passwordRecoveryExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void deliver(PasswordRecoveryDeliveryRequested event) {
        try {
            mailer.sendResetLink(event.recipientEmail(), event.rawResetToken());
        } catch (RuntimeException deliveryFailure) {
            // Never log the recipient email or the raw reset token. A later recovery request invalidates
            // the undisclosed token; normal retention cleanup removes it if delivery never succeeds.
            log.warn("Password recovery email delivery failed for user id {}", event.userId(), deliveryFailure);
        }
    }
}
