package com.doFast.dofastapp.user.auth.email;

public interface EmailVerificationMailer {
    void sendVerificationLink(String recipientEmail, String rawToken);
}
