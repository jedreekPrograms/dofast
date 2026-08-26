package com.doFast.dofastapp.user.auth.apple;

import com.doFast.dofastapp.common.exception.AuthenticationFailedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;

@Component
public class AppleIdentityTokenVerifier {

    private static final String APPLE_ISSUER = "https://appleid.apple.com";

    private final AppleAuthConfiguration configuration;
    private final NimbusJwtDecoder decoder;

    public AppleIdentityTokenVerifier(AppleAuthConfiguration configuration) {
        this.configuration = configuration;
        this.decoder = NimbusJwtDecoder.withJwkSetUri("https://appleid.apple.com/auth/keys").build();
        this.decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(APPLE_ISSUER));
    }

    public AppleIdentity verify(String idToken, String expectedNonce, String displayName) {
        try {
            Jwt jwt = decoder.decode(idToken);
            String subject = trimToNull(jwt.getSubject());
            List<String> audience = jwt.getAudience();
            String nonce = trimToNull(jwt.getClaimAsString("nonce"));
            String email = normalizeEmail(jwt.getClaimAsString("email"));
            boolean emailVerified = claimAsBoolean(jwt.getClaims().get("email_verified"));
            boolean privateRelayEmail = claimAsBoolean(jwt.getClaims().get("is_private_email"));

            if (subject == null
                    || audience == null
                    || !audience.contains(configuration.clientId())
                    || nonce == null
                    || !constantTimeEquals(nonce, expectedNonce)) {
                throw invalidCredential();
            }
            if (email != null && !emailVerified) {
                throw invalidCredential();
            }

            return new AppleIdentity(subject, email, trimToNull(displayName), privateRelayEmail);
        } catch (JwtException exception) {
            throw invalidCredential();
        }
    }

    private boolean claimAsBoolean(Object value) {
        if (value instanceof Boolean booleanValue) return booleanValue;
        if (value instanceof String stringValue) return Boolean.parseBoolean(stringValue);
        return false;
    }

    private boolean constantTimeEquals(String left, String right) {
        if (right == null) return false;
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String normalizeEmail(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private AuthenticationFailedException invalidCredential() {
        return new AuthenticationFailedException("Nieprawidłowa tożsamość Apple");
    }
}