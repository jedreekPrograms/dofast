package com.doFast.dofastapp.user.auth.apple;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AppleAuthConfiguration {

    private final String clientId;
    private final String redirectUri;
    private final String teamId;
    private final String keyId;
    private final String privateKeyBase64;
    private final long challengeTtlMinutes;

    public AppleAuthConfiguration(
            @Value("${APPLE_AUTH_CLIENT_ID:}") String clientId,
            @Value("${APPLE_AUTH_REDIRECT_URI:}") String redirectUri,
            @Value("${APPLE_AUTH_TEAM_ID:}") String teamId,
            @Value("${APPLE_AUTH_KEY_ID:}") String keyId,
            @Value("${APPLE_AUTH_PRIVATE_KEY_BASE64:}") String privateKeyBase64,
            @Value("${APPLE_AUTH_CHALLENGE_TTL_MINUTES:10}") long challengeTtlMinutes
    ) {
        this.clientId = trim(clientId);
        this.redirectUri = trim(redirectUri);
        this.teamId = trim(teamId);
        this.keyId = trim(keyId);
        this.privateKeyBase64 = trim(privateKeyBase64);
        this.challengeTtlMinutes = Math.max(1, Math.min(challengeTtlMinutes, 30));
    }

    public boolean isConfigured() {
        return !clientId.isBlank()
                && !redirectUri.isBlank()
                && !teamId.isBlank()
                && !keyId.isBlank()
                && !privateKeyBase64.isBlank();
    }

    public String clientId() { return clientId; }
    public String redirectUri() { return redirectUri; }
    public String teamId() { return teamId; }
    public String keyId() { return keyId; }
    public String privateKeyBase64() { return privateKeyBase64; }
    public long challengeTtlMinutes() { return challengeTtlMinutes; }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}