package com.doFast.dofastapp.user.auth.password;

import com.doFast.dofastapp.common.exception.BusinessException;
import com.doFast.dofastapp.user.auth.session.AuthRefreshSessionRepository;
import com.doFast.dofastapp.user.auth.session.AuthSessionSecrets;
import com.doFast.dofastapp.user.dto.ResetPasswordRequest;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.enums.UserRole;
import com.doFast.dofastapp.user.enums.UserStatus;
import com.doFast.dofastapp.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordRecoveryServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordResetTokenRepository tokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthRefreshSessionRepository refreshSessionRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private AuthSessionSecrets secrets;
    private PasswordRecoveryService service;

    @BeforeEach
    void setUp() {
        secrets = new AuthSessionSecrets();
        PasswordRecoveryProperties properties = new PasswordRecoveryProperties(
                30,
                7,
                "smtp",
                "https://app.example.test/reset-password",
                "security@example.test"
        );
        service = new PasswordRecoveryService(
                userRepository,
                tokenRepository,
                passwordEncoder,
                secrets,
                refreshSessionRepository,
                properties,
                eventPublisher
        );
    }

    @Test
    void unknownEmailDoesNotCreateTokenOrQueueDelivery() {
        when(userRepository.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());

        service.requestReset(" Missing@Example.com ");

        verify(tokenRepository, never()).saveAndFlush(any(PasswordResetToken.class));
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void requestInvalidatesPreviousTokensPersistsOnlyHashAndQueuesRawTokenInMemory() {
        User user = activeLocalUser();
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(tokenRepository.saveAndFlush(any(PasswordResetToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.requestReset("USER@example.com");

        verify(tokenRepository).invalidateActiveForUser(eq(9L), any(LocalDateTime.class));
        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).saveAndFlush(tokenCaptor.capture());
        PasswordResetToken stored = tokenCaptor.getValue();
        assertEquals(64, stored.getTokenHash().length());

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        PasswordRecoveryDeliveryRequested event = (PasswordRecoveryDeliveryRequested) eventCaptor.getValue();
        assertEquals(9L, event.userId());
        assertEquals("user@example.com", event.recipientEmail());
        assertNotEquals(event.rawResetToken(), stored.getTokenHash());
        assertEquals(stored.getTokenHash(), secrets.hash(event.rawResetToken()));
    }

    @Test
    void validResetChangesPasswordConsumesTokenAndRevokesSessions() {
        User user = activeLocalUser();
        String rawToken = "known-reset-token";
        PasswordResetToken token = PasswordResetToken.create(
                user,
                secrets.hash(rawToken),
                LocalDateTime.now().minusMinutes(2),
                LocalDateTime.now().plusMinutes(20)
        );
        ReflectionTestUtils.setField(token, "id", 41L);

        when(tokenRepository.findByTokenHashForUpdate(secrets.hash(rawToken))).thenReturn(Optional.of(token));
        when(userRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("NewPass456!", "old-hash")).thenReturn(false);
        when(passwordEncoder.encode("NewPass456!")).thenReturn("new-hash");
        when(userRepository.save(user)).thenReturn(user);
        when(tokenRepository.saveAndFlush(token)).thenReturn(token);

        service.resetPassword(new ResetPasswordRequest(rawToken, "NewPass456!"));

        assertEquals("new-hash", user.getPassword());
        assertEquals(1L, user.getAuthVersion());
        verify(tokenRepository).invalidateOtherActiveForUser(eq(9L), eq(41L), any(LocalDateTime.class));
        verify(refreshSessionRepository).revokeAllActiveForUser(
                eq(9L),
                eq("PASSWORD_RESET"),
                any(LocalDateTime.class)
        );
    }

    @Test
    void expiredTokenIsInvalidatedAndRejected() {
        User user = activeLocalUser();
        String rawToken = "expired-reset-token";
        PasswordResetToken token = PasswordResetToken.create(
                user,
                secrets.hash(rawToken),
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().minusMinutes(1)
        );
        when(tokenRepository.findByTokenHashForUpdate(secrets.hash(rawToken))).thenReturn(Optional.of(token));

        assertThrows(
                BusinessException.class,
                () -> service.resetPassword(new ResetPasswordRequest(rawToken, "NewPass456!"))
        );

        verify(tokenRepository).save(token);
        verify(userRepository, never()).findByIdForUpdate(any());
    }

    private User activeLocalUser() {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", 9L);
        user.setEmail("user@example.com");
        user.setNickname("test-user");
        user.setPassword("old-hash");
        user.setPasswordLoginEnabled(true);
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
