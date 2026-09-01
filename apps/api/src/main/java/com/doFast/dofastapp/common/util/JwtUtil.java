package com.doFast.dofastapp.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtil {

    private static final long MIN_ACCESS_TTL_MS = 60_000L;
    private static final long MAX_ACCESS_TTL_MS = 15 * 60_000L;

    private final Key signingKey;
    private final long expirationMs;

    public JwtUtil(
            @Value("${dofast.security.jwt.secret}") String secret,
            @Value("${dofast.security.jwt.expiration-ms:600000}") long expirationMs
    ) {
        if (expirationMs < MIN_ACCESS_TTL_MS || expirationMs > MAX_ACCESS_TTL_MS) {
            throw new IllegalArgumentException("Access JWT TTL must be between 1 and 15 minutes");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(String email, long authVersion) {
        if (authVersion < 0) {
            throw new IllegalArgumentException("Authentication version cannot be negative");
        }
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .setSubject(email)
                .setId(UUID.randomUUID().toString())
                .claim("typ", "access")
                .claim("av", authVersion)
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + expirationMs))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Kept for focused tests and legacy callers that construct a version-zero user.
     * Production authentication paths should pass the user's current authVersion explicitly.
     */
    public String generateToken(String email) {
        return generateToken(email, 0L);
    }

    public AccessTokenIdentity parseAccessToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
        if (!"access".equals(claims.get("typ", String.class))) {
            throw new JwtException("JWT is not an access token");
        }
        String email = claims.getSubject();
        Object rawAuthVersion = claims.get("av");
        if (email == null || email.isBlank() || !(rawAuthVersion instanceof Number number)) {
            throw new JwtException("JWT access identity is incomplete");
        }
        long authVersion = number.longValue();
        if (authVersion < 0) {
            throw new JwtException("JWT authentication version is invalid");
        }
        Date expiration = claims.getExpiration();
        if (expiration == null) {
            throw new JwtException("JWT access expiration is missing");
        }
        return new AccessTokenIdentity(email, authVersion, expiration.toInstant());
    }

    public String extractEmail(String token) {
        return parseAccessToken(token).email();
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public record AccessTokenIdentity(String email, long authVersion, Instant expiresAt) {}
}
