package com.doFast.dofastapp.user.auth.email;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
@ConditionalOnProperty(prefix = "dofast.security.email-verification", name = "delivery", havingValue = "smtp")
public class SmtpEmailVerificationMailer implements EmailVerificationMailer {
    private final JavaMailSender mailSender;
    private final EmailVerificationProperties properties;

    public SmtpEmailVerificationMailer(JavaMailSender mailSender, EmailVerificationProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void sendVerificationLink(String recipientEmail, String rawToken) {
        if (recipientEmail == null || recipientEmail.isBlank() || rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("Complete email verification delivery data is required");
        }
        String url = properties.verifyBaseUrl() + "?token=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.fromAddress());
        message.setTo(recipientEmail);
        message.setSubject("Zweryfikuj adres email w doFast");
        message.setText("""
                Dokończ rejestrację konta doFast, potwierdzając swój adres email:
                %s

                Link jest jednorazowy i wygasa. Jeśli nie zakładałeś konta doFast, zignoruj tę wiadomość.
                """.formatted(url));
        mailSender.send(message);
    }
}
