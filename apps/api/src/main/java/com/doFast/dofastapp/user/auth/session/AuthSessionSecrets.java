package com.doFast.dofastapp.user.auth.session;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Component
public class AuthSessionSecrets {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int SECRET_BYTES = 32;

    public String generate() {
        byte[] random = new byte[SECRET_BYTES];
        SECURE_RANDOM.nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    public String hash(String rawSecret) {
        if (rawSecret == null || rawSecret.isBlank()) {
            throw new IllegalArgumentException("Session secret is required");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawSecret.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    public boolean matchesHash(String rawSecret, String expectedHash) {
        if (rawSecret == null || expectedHash == null) return false;
        byte[] actual = hash(rawSecret).getBytes(StandardCharsets.US_ASCII);
        byte[] expected = expectedHash.getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(actual, expected);
    }

    public boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) return false;
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8)
        );
    }
}
