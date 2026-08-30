package com.doFast.dofastapp.user.auth.password;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
@ConditionalOnProperty(
        prefix = "dofast.security.password-recovery",
        name = "delivery",
        havingValue = "smtp"
)
public class SmtpPasswordRecoveryMailer implements PasswordRecoveryMailer {

    private final JavaMailSender mailSender;
    private final PasswordRecoveryProperties properties;

    public SmtpPasswordRecoveryMailer(
            JavaMailSender mailSender,
            PasswordRecoveryProperties properties
    ) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void sendResetLink(String recipientEmail, String rawResetToken) {
        if (recipientEmail == null || recipientEmail.isBlank() || rawResetToken == null || rawResetToken.isBlank()) {
            throw new IllegalArgumentException("Complete password recovery delivery data is required");
        }

        String token = URLEncoder.encode(rawResetToken, StandardCharsets.UTF_8);
        String resetUrl = properties.resetBaseUrl() + "?token=" + token;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.fromAddress());
        message.setTo(recipientEmail);
        message.setSubject("Reset hasła doFast");
        message.setText("""
                Otrzymaliśmy prośbę o zmianę hasła do Twojego konta doFast.

                Ustaw nowe hasło korzystając z poniższego linku:
                %s

                Link jest jednorazowy i szybko wygasa. Jeśli to nie Ty poprosiłeś o reset hasła, zignoruj tę wiadomość.
                """.formatted(resetUrl));
        mailSender.send(message);
    }
}
