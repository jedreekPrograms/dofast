package com.doFast.dofastapp.user.auth.password;

public record PasswordRecoveryDeliveryRequested(
        Long userId,
        String recipientEmail,
        String rawResetToken
) {
    public PasswordRecoveryDeliveryRequested {
        if (userId == null || recipientEmail == null || recipientEmail.isBlank()
                || rawResetToken == null || rawResetToken.isBlank()) {
            throw new IllegalArgumentException("Complete password recovery delivery event is required");
        }
    }
}
