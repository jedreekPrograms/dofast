package com.doFast.dofastapp.user.auth.apple;

import com.doFast.dofastapp.common.exception.AuthenticationFailedException;
import com.doFast.dofastapp.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class AppleLoginChallengeService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AppleLoginChallengeRepository repository;
    private final AppleAuthConfiguration configuration;

    public AppleLoginChallengeService(
            AppleLoginChallengeRepository repository,
            AppleAuthConfiguration configuration
    ) {
        this.repository = repository;
        this.configuration = configuration;
    }

    @Transactional
    public AppleLoginChallengeResponse createChallenge() {
        requireConfigured();
        Instant now = Instant.now();
        repository.deleteByExpiresAtBefore(now.minus(1, ChronoUnit.DAYS));

        String state = randomToken();
        String nonce = randomToken();
        AppleLoginChallenge challenge = new AppleLoginChallenge();
        challenge.setId(UUID.randomUUID());
        challenge.setStateHash(sha256Hex(state));
        challenge.setNonceHash(sha256Hex(nonce));
        challenge.setCreatedAt(now);
        challenge.setExpiresAt(now.plus(configuration.challengeTtlMinutes(), ChronoUnit.MINUTES));
        repository.save(challenge);

        return new AppleLoginChallengeResponse(
                challenge.getId(),
                state,
                nonce,
                challenge.getExpiresAt()
        );
    }

    @Transactional
    public void consume(UUID challengeId, String state, String nonce) {
        AppleLoginChallenge challenge = repository.findByIdForUpdate(challengeId)
                .orElseThrow(this::invalidChallenge);
        Instant now = Instant.now();

        if (challenge.getConsumedAt() != null || !challenge.getExpiresAt().isAfter(now)) {
            throw invalidChallenge();
        }
        if (!constantTimeHashEquals(challenge.getStateHash(), state)
                || !constantTimeHashEquals(challenge.getNonceHash(), nonce)) {
            throw invalidChallenge();
        }

        challenge.setConsumedAt(now);
        repository.save(challenge);
    }

    private void requireConfigured() {
        if (!configuration.isConfigured()) {
            throw new BusinessException("Logowanie przez Apple nie jest skonfigurowane");
        }
    }

    private AuthenticationFailedException invalidChallenge() {
        return new AuthenticationFailedException("Sesja logowania Apple wygasła lub jest nieprawidłowa");
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private boolean constantTimeHashEquals(String expectedHex, String value) {
        if (value == null || value.isBlank()) return false;
        byte[] expected = HexFormat.of().parseHex(expectedHex);
        byte[] actual = sha256(value);
        return MessageDigest.isEqual(expected, actual);
    }

    private String sha256Hex(String value) {
        return HexFormat.of().formatHex(sha256(value));
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}