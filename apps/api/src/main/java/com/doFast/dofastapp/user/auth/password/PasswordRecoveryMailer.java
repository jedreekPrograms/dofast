package com.doFast.dofastapp.user.auth.password;

public interface PasswordRecoveryMailer {
    void sendResetLink(String recipientEmail, String rawResetToken);
}
