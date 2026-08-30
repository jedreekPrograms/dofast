package com.doFast.dofastapp.user.auth.email;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "dofast.security.email-verification",
        name = "delivery",
        havingValue = "disabled",
        matchIfMissing = true
)
public class DisabledEmailVerificationMailer implements EmailVerificationMailer {
    @Override
    public void sendVerificationLink(String recipientEmail, String rawToken) {
        // Local/default environments intentionally do not send email. When verification is not
        // required, the service marks local accounts verified immediately and never issues a token.
    }
}
