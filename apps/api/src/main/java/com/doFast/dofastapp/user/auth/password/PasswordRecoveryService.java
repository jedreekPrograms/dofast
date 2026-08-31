package com.doFast.dofastapp.user.auth.password;

import com.doFast.dofastapp.common.exception.BusinessException;
import com.doFast.dofastapp.user.auth.session.AuthRefreshSessionRepository;
import com.doFast.dofastapp.user.auth.session.AuthSessionSecrets;
import com.doFast.dofastapp.user.dto.ResetPasswordRequest;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;

@Service
public class PasswordRecoveryService {

    private static final String PASSWORD_RESET_SESSION_REASON = "PASSWORD_RESET";

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthSessionSecrets secrets;
    private final AuthRefreshSessionRepository refreshSessionRepository;
    private final PasswordRecoveryProperties properties;
    private final ApplicationEventPublisher eventPublisher;

    public PasswordRecoveryService(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            PasswordEncoder passwordEncoder,
            AuthSessionSecrets secrets,
            AuthRefreshSessionRepository refreshSessionRepository,
            PasswordRecoveryProperties properties,
            ApplicationEventPublisher eventPublisher
    ) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.secrets = secrets;
        this.refreshSessionRepository = refreshSessionRepository;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void requestReset(String rawEmail) {
        if (!properties.smtpEnabled()) {
            return;
        }

        String email = normalizeEmail(rawEmail);
        User candidate = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (candidate == null || !candidate.isPasswordLoginEnabled()) {
            return;
        }

        // Serialize forgot/reset work by the user row. Without this lock, two concurrent forgot
        // requests can both invalidate the previous token and then each create a fresh active one.
        User user = userRepository.findByIdForUpdate(candidate.getId()).orElse(null);
        if (user == null || !user.isPasswordLoginEnabled()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        if (tokenRepository.existsByUserIdAndCreatedAtAfter(
                user.getId(),
                now.minus(properties.requestCooldown())
        )) {
            // Keep the public endpoint indistinguishable from a normal accepted request while
            // suppressing repeated SMTP delivery and token churn for the same account.
            return;
        }

        tokenRepository.invalidateActiveForUser(user.getId(), now);

        String rawToken = secrets.generate();
        PasswordResetToken token = PasswordResetToken.create(
                user,
                secrets.hash(rawToken),
                now,
                now.plus(properties.tokenTtl())
        );
        tokenRepository.saveAndFlush(token);

        eventPublisher.publishEvent(new PasswordRecoveryDeliveryRequested(
                user.getId(),
                user.getEmail(),
                rawToken
        ));
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public void resetPassword(ResetPasswordRequest request) {
        String tokenHash = secrets.hash(request.token());

        // Probe only to discover the owner, then acquire locks in the same user -> token order as
        // forgot-password. The token is fetched again under PESSIMISTIC_WRITE before any decision.
        PasswordResetToken probe = tokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(this::invalidResetToken);
        Long userId = probe.getUser().getId();
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(this::invalidResetToken);
        PasswordResetToken token = tokenRepository.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(this::invalidResetToken);

        if (!Objects.equals(token.getUser().getId(), user.getId())) {
            throw invalidResetToken();
        }

        LocalDateTime now = LocalDateTime.now();
        if (!token.activeAt(now)) {
            if (token.getUsedAt() == null && token.getInvalidatedAt() == null) {
                token.invalidate(now);
                tokenRepository.save(token);
            }
            throw invalidResetToken();
        }

        if (!user.isPasswordLoginEnabled()) {
            token.invalidate(now);
            tokenRepository.save(token);
            throw invalidResetToken();
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new BusinessException("Nowe hasło musi różnić się od aktualnego");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.incrementAuthVersion();
        userRepository.save(user);

        token.markUsed(now);
        tokenRepository.saveAndFlush(token);
        tokenRepository.invalidateOtherActiveForUser(user.getId(), token.getId(), now);
        refreshSessionRepository.revokeAllActiveForUser(
                user.getId(),
                PASSWORD_RESET_SESSION_REASON,
                now
        );
    }

    @Transactional
    public int cleanupOldTokens() {
        return tokenRepository.deleteExpiredOrConsumedBefore(LocalDateTime.now().minus(properties.retention()));
    }

    private BusinessException invalidResetToken() {
        return new BusinessException("Link resetu hasła jest nieprawidłowy lub wygasł");
    }

    private String normalizeEmail(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
