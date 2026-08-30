package com.doFast.dofastapp.user.auth.email;

import com.doFast.dofastapp.user.auth.session.AuthSessionSecrets;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {
    @Mock private UserRepository userRepository;
    @Mock private EmailVerificationTokenRepository tokenRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private AuthSessionSecrets secrets;

    @BeforeEach
    void setUp() {
        secrets = new AuthSessionSecrets();
    }

    @Test
    void optionalVerificationMarksLocalAccountVerifiedWithoutCreatingToken() {
        EmailVerificationService service = service(false, "disabled");
        User user = localUser();
        when(userRepository.save(user)).thenReturn(user);

        service.initializeLocalAccount(user);

        assertTrue(user.isEmailVerified());
        verify(tokenRepository, never()).saveAndFlush(any());
    }

    @Test
    void requiredVerificationStoresOnlyHashAndQueuesRawTokenAfterCommitBoundary() {
        EmailVerificationService service = service(true, "smtp");
        User user = localUser();
        when(userRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(user));
        when(tokenRepository.saveAndFlush(any(EmailVerificationToken.class))).thenAnswer(i -> i.getArgument(0));

        service.initializeLocalAccount(user);

        assertFalse(user.isEmailVerified());
        ArgumentCaptor<EmailVerificationToken> tokenCaptor = ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(tokenRepository).saveAndFlush(tokenCaptor.capture());
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        EmailVerificationDeliveryRequested event = (EmailVerificationDeliveryRequested) eventCaptor.getValue();
        assertTrue(tokenCaptor.getValue().getTokenHash().length() == 64);
        assertTrue(tokenCaptor.getValue().getTokenHash().equals(secrets.hash(event.rawToken())));
    }

    @Test
    void validTokenVerifiesUserAndConsumesCredential() {
        EmailVerificationService service = service(true, "smtp");
        User user = localUser();
        String raw = "known-email-token";
        EmailVerificationToken token = EmailVerificationToken.create(
                user,
                secrets.hash(raw),
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusHours(1)
        );
        ReflectionTestUtils.setField(token, "id", 31L);
        when(tokenRepository.findByTokenHash(secrets.hash(raw))).thenReturn(Optional.of(token));
        when(userRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(user));
        when(tokenRepository.findByTokenHashForUpdate(secrets.hash(raw))).thenReturn(Optional.of(token));
        when(userRepository.save(user)).thenReturn(user);
        when(tokenRepository.saveAndFlush(token)).thenReturn(token);

        service.verify(raw);

        assertTrue(user.isEmailVerified());
        verify(tokenRepository).invalidateActiveForUser(eq(9L), any(LocalDateTime.class));
    }

    @Test
    void resendForUnknownAddressIsEnumerationSafe() {
        EmailVerificationService service = service(true, "smtp");
        when(userRepository.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());

        service.resend(" Missing@Example.com ");

        verify(tokenRepository, never()).saveAndFlush(any());
    }

    private EmailVerificationService service(boolean required, String delivery) {
        EmailVerificationProperties properties = new EmailVerificationProperties(
                24,
                7,
                required,
                delivery,
                required ? "https://app.example.test/verify-email" : "",
                required ? "security@example.test" : ""
        );
        return new EmailVerificationService(userRepository, tokenRepository, secrets, properties, eventPublisher);
    }

    private User localUser() {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", 9L);
        user.setEmail("user@example.com");
        user.setNickname("test-user");
        user.setPassword("hash");
        user.setPasswordLoginEnabled(true);
        return user;
    }
}
