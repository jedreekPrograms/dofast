package com.doFast.dofastapp.user.auth.password;

import com.doFast.dofastapp.common.exception.BusinessException;
import com.doFast.dofastapp.user.auth.session.AuthRefreshSessionRepository;
import com.doFast.dofastapp.user.auth.session.AuthSessionSecrets;
import com.doFast.dofastapp.user.dto.ResetPasswordRequest;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class PasswordRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(PasswordRecoveryService.class);
    private static final String PASSWORD_RESET_SESSION_REASON = "PASSWORD_RESET";

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthSessionSecrets secrets;
    private final AuthRefreshSessionRepository refreshSessionRepository;
    private final PasswordRecoveryProperties properties;
    private final PasswordRecoveryMailer mailer;

    public PasswordRecoveryService(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            PasswordEncoder passwordEncoder,
            AuthSessionSecrets secrets,
            AuthRefreshSessionRepository refreshSessionRepository,
            PasswordRecoveryProperties properties,
            PasswordRecoveryMailer mailer
    ) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.secrets = secrets;
        this.refreshSessionRepository = refreshSessionRepository;
        this.properties = properties;
        this.mailer = mailer;
    }

    @Transactional
    public void requestReset(String rawEmail) {
        if (!properties.smtpEnabled()) {
            return;
        }

        String email = normalizeEmail(rawEmail);
        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null || !user.isPasswordLoginEnabled()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        tokenRepository.invalidateActiveForUser(user.getId(), now);

        String rawToken = secrets.generate();
        PasswordResetToken token = PasswordResetToken.create(
                user,
                secrets.hash(rawToken),
                now,
                now.plus(properties.tokenTtl())
        );
        tokenRepository.saveAndFlush(token);

        try {
            mailer.sendResetLink(user.getEmail(), rawToken);
        } catch (RuntimeException deliveryFailure) {
            token.invalidate(now);
            tokenRepository.save(token);
            log.warn("Password recovery email delivery failed for user id {}", user.getId(), deliveryFailure);
        }
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        LocalDateTime now = LocalDateTime.now();
        PasswordResetToken token = tokenRepository.findByTokenHashForUpdate(secrets.hash(request.token()))
                .orElseThrow(this::invalidResetToken);

        if (!token.activeAt(now)) {
            if (token.getUsedAt() == null && token.getInvalidatedAt() == null) {
                token.invalidate(now);
                tokenRepository.save(token);
            }
            throw invalidResetToken();
        }

        User user = userRepository.findByIdForUpdate(token.getUser().getId())
                .orElseThrow(this::invalidResetToken);
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
