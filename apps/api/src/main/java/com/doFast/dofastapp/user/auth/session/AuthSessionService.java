package com.doFast.dofastapp.user.auth.session;

import com.doFast.dofastapp.common.exception.AuthenticationFailedException;
import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.common.util.JwtUtil;
import com.doFast.dofastapp.user.dto.AuthResponse;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.enums.UserStatus;
import com.doFast.dofastapp.user.repository.UserRepository;
import com.doFast.dofastapp.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuthSessionService {

    static final String ROTATED = "ROTATED";
    static final String LOGOUT = "LOGOUT";
    static final String EXPIRED = "EXPIRED";
    static final String REUSE_DETECTED = "REUSE_DETECTED";
    static final String USER_INACTIVE = "USER_INACTIVE";
    static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";

    private final AuthRefreshSessionRepository sessionRepository;
    private final AuthSessionSecrets secrets;
    private final AuthSessionProperties properties;
    private final JwtUtil jwtUtil;
    private final UserService userService;
    private final UserRepository userRepository;

    public AuthSessionService(
            AuthRefreshSessionRepository sessionRepository,
            AuthSessionSecrets secrets,
            AuthSessionProperties properties,
            JwtUtil jwtUtil,
            UserService userService,
            UserRepository userRepository
    ) {
        this.sessionRepository = sessionRepository;
        this.secrets = secrets;
        this.properties = properties;
        this.jwtUtil = jwtUtil;
        this.userService = userService;
        this.userRepository = userRepository;
    }

    @Transactional
    public AuthSessionGrant issue(AuthResponse authResponse) {
        if (authResponse == null || authResponse.user() == null || authResponse.user().id() == null
                || authResponse.accessToken() == null || authResponse.accessToken().isBlank()) {
            throw new IllegalArgumentException("Complete authentication response is required");
        }
        User user = userRepository.findById(authResponse.user().id())
                .orElseThrow(this::invalidSession);
        requireActive(user);
        return issueGrant(user, UUID.randomUUID(), LocalDateTime.now(), authResponse);
    }

    @Transactional(noRollbackFor = {AuthenticationFailedException.class, ForbiddenOperationException.class})
    public AuthSessionGrant refresh(String rawRefreshToken, String rawCsrfToken) {
        LocalDateTime now = LocalDateTime.now();
        AuthRefreshSession current = sessionRepository.findByTokenHashForUpdate(secrets.hash(rawRefreshToken))
                .orElseThrow(this::invalidSession);

        requireCsrf(current, rawCsrfToken);

        if (current.revoked()) {
            handleReplay(current, now);
            throw invalidSession();
        }
        if (current.expiredAt(now)) {
            current.revoke(EXPIRED, now);
            sessionRepository.save(current);
            throw invalidSession();
        }

        User user = current.getUser();
        if (user.getStatus() != UserStatus.ACTIVE) {
            sessionRepository.revokeActiveFamily(current.getFamilyId(), USER_INACTIVE, now);
            throw new ForbiddenOperationException("Konto jest obecnie zawieszone");
        }

        String accessToken = jwtUtil.generateToken(user.getEmail());
        AuthResponse response = new AuthResponse(
                accessToken,
                "Bearer",
                jwtUtil.getExpirationMs(),
                userService.toResponse(user)
        );
        AuthSessionGrant successor = issueGrant(user, current.getFamilyId(), now, response);
        current.markUsed(now);
        current.revoke(ROTATED, now);
        sessionRepository.save(current);
        return successor;
    }

    @Transactional
    public void logout(String rawRefreshToken, String rawCsrfToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) return;

        AuthRefreshSession current = sessionRepository.findByTokenHashForUpdate(secrets.hash(rawRefreshToken))
                .orElse(null);
        if (current == null) return;

        requireCsrf(current, rawCsrfToken);
        LocalDateTime now = LocalDateTime.now();
        if (current.revoked()) {
            if (ROTATED.equals(current.getRevocationReason())) {
                sessionRepository.revokeActiveFamily(current.getFamilyId(), LOGOUT, now);
            }
            return;
        }
        current.markUsed(now);
        current.revoke(LOGOUT, now);
        sessionRepository.save(current);
    }

    @Transactional
    public void revokeAllForUser(Long userId, String reason) {
        if (userId == null) return;
        String normalizedReason = reason == null || reason.isBlank() ? PASSWORD_CHANGED : reason.trim();
        if (normalizedReason.length() > 32) normalizedReason = normalizedReason.substring(0, 32);
        sessionRepository.revokeAllActiveForUser(userId, normalizedReason, LocalDateTime.now());
    }

    @Transactional
    public int cleanupOldSessions() {
        return sessionRepository.deleteExpiredOrOldRevoked(LocalDateTime.now().minus(properties.retention()));
    }

    private AuthSessionGrant issueGrant(
            User user,
            UUID familyId,
            LocalDateTime now,
            AuthResponse response
    ) {
        String refreshToken = secrets.generate();
        String csrfToken = secrets.generate();
        AuthRefreshSession session = AuthRefreshSession.create(
                user,
                familyId,
                secrets.hash(refreshToken),
                secrets.hash(csrfToken),
                now,
                now.plus(properties.refreshTtl())
        );
        sessionRepository.saveAndFlush(session);
        return new AuthSessionGrant(response, refreshToken, csrfToken);
    }

    private void handleReplay(AuthRefreshSession current, LocalDateTime now) {
        if (!ROTATED.equals(current.getRevocationReason()) || current.getRevokedAt() == null) return;
        if (current.getRevokedAt().plus(properties.reuseGrace()).isBefore(now)) {
            sessionRepository.revokeActiveFamily(current.getFamilyId(), REUSE_DETECTED, now);
        }
    }

    private void requireCsrf(AuthRefreshSession session, String rawCsrfToken) {
        if (!secrets.matchesHash(rawCsrfToken, session.getCsrfHash())) {
            throw new ForbiddenOperationException("Nieprawidłowy token CSRF");
        }
    }

    private void requireActive(User user) {
        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            throw new ForbiddenOperationException("Konto jest obecnie zawieszone");
        }
    }

    private AuthenticationFailedException invalidSession() {
        return new AuthenticationFailedException("Sesja wygasła lub jest nieprawidłowa");
    }
}
