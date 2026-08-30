package com.doFast.dofastapp.user.auth.email;

import com.doFast.dofastapp.common.exception.BusinessException;
import com.doFast.dofastapp.user.auth.session.AuthSessionSecrets;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;

@Service
public class EmailVerificationService {
    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final AuthSessionSecrets secrets;
    private final EmailVerificationProperties properties;
    private final ApplicationEventPublisher eventPublisher;

    public EmailVerificationService(UserRepository userRepository,
                                    EmailVerificationTokenRepository tokenRepository,
                                    AuthSessionSecrets secrets,
                                    EmailVerificationProperties properties,
                                    ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.secrets = secrets;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void initializeLocalAccount(User user) {
        if (!properties.required()) {
            user.markEmailVerified(LocalDateTime.now());
            userRepository.save(user);
            return;
        }
        issueForUser(user.getId());
    }

    @Transactional
    public void resend(String rawEmail) {
        if (!properties.required()) return;
        User candidate = userRepository.findByEmailIgnoreCase(normalizeEmail(rawEmail)).orElse(null);
        if (candidate == null || !candidate.isPasswordLoginEnabled() || candidate.isEmailVerified()) return;
        issueForUser(candidate.getId());
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public void verify(String rawToken) {
        String tokenHash = secrets.hash(rawToken);
        EmailVerificationToken probe = tokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(this::invalidToken);
        Long userId = probe.getUser().getId();
        User user = userRepository.findByIdForUpdate(userId).orElseThrow(this::invalidToken);
        EmailVerificationToken token = tokenRepository.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(this::invalidToken);
        if (!Objects.equals(token.getUser().getId(), user.getId())) throw invalidToken();

        LocalDateTime now = LocalDateTime.now();
        if (!token.activeAt(now)) {
            if (token.getUsedAt() == null && token.getInvalidatedAt() == null) {
                token.invalidate(now);
                tokenRepository.save(token);
            }
            throw invalidToken();
        }
        if (!user.isPasswordLoginEnabled()) {
            token.invalidate(now);
            tokenRepository.save(token);
            throw invalidToken();
        }

        user.markEmailVerified(now);
        userRepository.save(user);
        token.markUsed(now);
        tokenRepository.saveAndFlush(token);
        tokenRepository.invalidateActiveForUser(user.getId(), now);
    }

    @Transactional
    public int cleanupOldTokens() {
        return tokenRepository.deleteExpiredOrConsumedBefore(LocalDateTime.now().minus(properties.retention()));
    }

    public boolean required() { return properties.required(); }

    private void issueForUser(Long userId) {
        User user = userRepository.findByIdForUpdate(userId).orElse(null);
        if (user == null || user.isEmailVerified() || !user.isPasswordLoginEnabled()) return;
        LocalDateTime now = LocalDateTime.now();
        tokenRepository.invalidateActiveForUser(user.getId(), now);
        String rawToken = secrets.generate();
        EmailVerificationToken token = EmailVerificationToken.create(
                user,
                secrets.hash(rawToken),
                now,
                now.plus(properties.tokenTtl())
        );
        tokenRepository.saveAndFlush(token);
        eventPublisher.publishEvent(new EmailVerificationDeliveryRequested(user.getId(), user.getEmail(), rawToken));
    }

    private BusinessException invalidToken() {
        return new BusinessException("Link weryfikacyjny jest nieprawidłowy lub wygasł");
    }

    private String normalizeEmail(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
