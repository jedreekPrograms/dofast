package com.doFast.dofastapp.user.auth.email;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(EmailVerificationMailer.class)
public class DisabledEmailVerificationMailer implements EmailVerificationMailer {
    @Override
    public void sendVerificationLink(String recipientEmail, String rawToken) {
        throw new IllegalStateException("Email verification delivery is disabled");
    }
}
