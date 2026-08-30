package com.doFast.dofastapp.user.auth.password;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "dofast.security.password-recovery",
        name = "delivery",
        havingValue = "disabled",
        matchIfMissing = true
)
public class DisabledPasswordRecoveryMailer implements PasswordRecoveryMailer {

    @Override
    public void sendResetLink(String recipientEmail, String rawResetToken) {
        // Intentionally disabled for local/default environments. The recovery service does not
        // create reset tokens when delivery is disabled, so no unusable credential is persisted.
    }
}
