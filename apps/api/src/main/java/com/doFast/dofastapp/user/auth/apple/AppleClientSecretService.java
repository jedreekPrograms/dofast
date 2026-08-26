package com.doFast.dofastapp.user.auth.apple;

import com.doFast.dofastapp.common.exception.BusinessException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;

@Service
public class AppleClientSecretService {

    private final AppleAuthConfiguration configuration;
    private final ECPrivateKey privateKey;

    public AppleClientSecretService(AppleAuthConfiguration configuration) {
        this.configuration = configuration;
        this.privateKey = configuration.isConfigured() ? parsePrivateKey(configuration.privateKeyBase64()) : null;
    }

    public String createClientSecret() {
        if (!configuration.isConfigured() || privateKey == null) {
            throw new BusinessException("Logowanie przez Apple nie jest skonfigurowane");
        }

        try {
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(configuration.teamId())
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plus(5, ChronoUnit.MINUTES)))
                    .audience("https://appleid.apple.com")
                    .subject(configuration.clientId())
                    .build();

            SignedJWT clientSecret = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.ES256)
                            .keyID(configuration.keyId())
                            .build(),
                    claims
            );
            clientSecret.sign(new ECDSASigner(privateKey));
            return clientSecret.serialize();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create Apple client secret", exception);
        }
    }

    private ECPrivateKey parsePrivateKey(String configuredValue) {
        try {
            byte[] decoded = Base64.getDecoder().decode(configuredValue);
            String maybePem = new String(decoded, StandardCharsets.US_ASCII).trim();
            byte[] der = maybePem.contains("BEGIN PRIVATE KEY")
                    ? decodePem(maybePem)
                    : decoded;

            PrivateKey key = KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der));
            if (!(key instanceof ECPrivateKey ecPrivateKey)) {
                throw new IllegalArgumentException("Apple private key is not an EC private key");
            }
            return ecPrivateKey;
        } catch (Exception exception) {
            throw new IllegalStateException("APPLE_AUTH_PRIVATE_KEY_BASE64 is invalid", exception);
        }
    }

    private byte[] decodePem(String pem) {
        String base64 = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64);
    }
}