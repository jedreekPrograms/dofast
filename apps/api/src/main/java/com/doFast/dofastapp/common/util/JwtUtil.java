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

    public String generateToken(String email) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .setSubject(email)
                .setId(UUID.randomUUID().toString())
                .claim("typ", "access")
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + expirationMs))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractEmail(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
        if (!"access".equals(claims.get("typ", String.class))) {
            throw new JwtException("JWT is not an access token");
        }
        return claims.getSubject();
    }

    public long getExpirationMs() {
        return expirationMs;
    }
}
