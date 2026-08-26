package com.doFast.dofastapp.user.auth;

import com.doFast.dofastapp.common.exception.AuthenticationFailedException;
import com.doFast.dofastapp.common.exception.BusinessException;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Locale;

@Component
public class GoogleIdentityTokenVerifier implements GoogleIdentityVerifier {

    private final GoogleIdTokenVerifier verifier;

    public GoogleIdentityTokenVerifier(@Value("${GOOGLE_AUTH_CLIENT_ID:}") String configuredClientId) {
        String clientId = configuredClientId == null ? "" : configuredClientId.trim();
        this.verifier = clientId.isBlank()
                ? null
                : new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                        .setAudience(List.of(clientId))
                        .build();
    }

    @Override
    public GoogleIdentity verify(String credential) {
        if (verifier == null) {
            throw new BusinessException("Logowanie przez Google nie jest skonfigurowane");
        }

        try {
            GoogleIdToken idToken = verifier.verify(credential);
            if (idToken == null) {
                throw invalidCredential();
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            String subject = trimToNull(payload.getSubject());
            String email = normalizeEmail(payload.getEmail());
            Boolean emailVerified = payload.getEmailVerified();

            if (subject == null || email == null || !Boolean.TRUE.equals(emailVerified)) {
                throw invalidCredential();
            }

            String hostedDomain = trimToNull(payload.getHostedDomain());
            boolean authoritativeForEmail = email.endsWith("@gmail.com") || hostedDomain != null;
            String displayName = trimToNull((String) payload.get("name"));

            return new GoogleIdentity(subject, email, displayName, authoritativeForEmail);
        } catch (GeneralSecurityException | IOException exception) {
            throw invalidCredential();
        }
    }

    private AuthenticationFailedException invalidCredential() {
        return new AuthenticationFailedException("Nieprawidłowe poświadczenie Google");
    }

    private String normalizeEmail(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}