package com.doFast.dofastapp.user;

import com.doFast.dofastapp.common.exception.AuthenticationFailedException;
import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.common.util.JwtUtil;
import com.doFast.dofastapp.user.auth.GoogleIdentity;
import com.doFast.dofastapp.user.auth.GoogleIdentityVerifier;
import com.doFast.dofastapp.user.auth.apple.AppleIdentity;
import com.doFast.dofastapp.user.auth.email.EmailVerificationService;
import com.doFast.dofastapp.user.auth.session.AuthRefreshSessionRepository;
import com.doFast.dofastapp.user.dto.AuthResponse;
import com.doFast.dofastapp.user.dto.ChangePasswordRequest;
import com.doFast.dofastapp.user.dto.GoogleLoginRequest;
import com.doFast.dofastapp.user.dto.LoginRequest;
import com.doFast.dofastapp.user.dto.UpdateProfileRequest;
import com.doFast.dofastapp.user.dto.UserRequest;
import com.doFast.dofastapp.user.dto.UserResponse;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.entity.UserAuthIdentity;
import com.doFast.dofastapp.user.enums.AuthProvider;
import com.doFast.dofastapp.user.enums.UserRole;
import com.doFast.dofastapp.user.enums.UserStatus;
import com.doFast.dofastapp.user.repository.UserAuthIdentityRepository;
import com.doFast.dofastapp.user.repository.UserRepository;
import com.doFast.dofastapp.user.service.UserService;
import com.doFast.dofastapp.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @Mock private WalletService walletService;
    @Mock private UserAuthIdentityRepository authIdentityRepository;
    @Mock private GoogleIdentityVerifier googleIdentityVerifier;
    @Mock private AuthRefreshSessionRepository refreshSessionRepository;
    @Mock private EmailVerificationService emailVerificationService;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(
                userRepository,
                passwordEncoder,
                jwtUtil,
                walletService,
                authIdentityRepository,
                googleIdentityVerifier,
                refreshSessionRepository,
                emailVerificationService
        );
    }

    @Test
    void accountOperationsFailClosedBeforePersistenceForTransientIdentity() {
        User transientUser = new User("transient@example.com", "transient");
        UpdateProfileRequest profile = new UpdateProfileRequest("nickname", null, null);
        ChangePasswordRequest password = new ChangePasswordRequest("OldPass123!", "NewPass456!");

        assertThrows(ForbiddenOperationException.class, () -> userService.getCurrentUser(null));
        assertThrows(ForbiddenOperationException.class, () -> userService.getCurrentUser(transientUser));
        assertThrows(ForbiddenOperationException.class, () -> userService.updateProfile(transientUser, profile));
        assertThrows(ForbiddenOperationException.class, () -> userService.changePassword(transientUser, password));

        verifyNoInteractions(
                userRepository,
                passwordEncoder,
                jwtUtil,
                walletService,
                authIdentityRepository,
                googleIdentityVerifier,
                refreshSessionRepository,
                emailVerificationService
        );
    }

    @Test
    void publicRegistrationAlwaysCreatesActiveRegularUserAndNormalizesEmail() {
        UserRequest request = new UserRequest();
        request.setEmail("  Example@Email.COM ");
        request.setNickname("example-user");
        request.setPassword("StrongPass123!");

        when(userRepository.existsByEmailIgnoreCase("example@email.com")).thenReturn(false);
        when(passwordEncoder.encode("StrongPass123!")).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 15L);
            return saved;
        });

        UserResponse response = userService.createUser(request);

        assertEquals("example@email.com", response.email());
        assertEquals(UserRole.USER, response.role());
        assertEquals(UserStatus.ACTIVE, response.status());
        verify(walletService).createWalletForUser(15L);
        verify(emailVerificationService).initializeLocalAccount(any(User.class));
    }

    @Test
    void invalidPasswordDoesNotRevealWhetherEmailExists() {
        LoginRequest request = new LoginRequest();
        request.setEmail("user@example.com");
        request.setPassword("wrong-password");

        User user = activeUser();
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "hash")).thenReturn(false);

        AuthenticationFailedException exception = assertThrows(
                AuthenticationFailedException.class,
                () -> userService.login(request)
        );
        assertEquals("Nieprawidłowy email lub hasło", exception.getMessage());
    }

    @Test
    void oauthOnlyAccountCannotUsePasswordLogin() {
        LoginRequest request = new LoginRequest();
        request.setEmail("user@example.com");
        request.setPassword("anything");

        User user = activeUser();
        user.setPasswordLoginEnabled(false);
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));

        assertThrows(AuthenticationFailedException.class, () -> userService.login(request));
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void suspendedUserCannotLogIn() {
        LoginRequest request = new LoginRequest();
        request.setEmail("user@example.com");
        request.setPassword("StrongPass123!");

        User user = activeUser();
        user.setStatus(UserStatus.SUSPENDED);
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("StrongPass123!", "hash")).thenReturn(true);

        assertThrows(ForbiddenOperationException.class, () -> userService.login(request));
    }

    @Test
    void requiredVerificationBlocksValidPasswordUntilEmailIsVerified() {
        LoginRequest request = new LoginRequest();
        request.setEmail("user@example.com");
        request.setPassword("StrongPass123!");
        User user = activeUser();
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("StrongPass123!", "hash")).thenReturn(true);
        when(emailVerificationService.required()).thenReturn(true);

        assertThrows(ForbiddenOperationException.class, () -> userService.login(request));
    }

    @Test
    void successfulLoginReturnsBearerTokenAndCurrentRole() {
        LoginRequest request = new LoginRequest();
        request.setEmail("user@example.com");
        request.setPassword("StrongPass123!");

        User user = activeUser();
        user.markEmailVerified(LocalDateTime.now());
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("StrongPass123!", "hash")).thenReturn(true);
        stubJwt(user);

        AuthResponse response = userService.login(request);

        assertEquals("token", response.accessToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(UserRole.USER, response.user().role());
    }

    @Test
    void passwordChangeRevokesAllSessionsAndIncrementsCredentialVersion() {
        User user = activeUser();
        ChangePasswordRequest request = new ChangePasswordRequest("OldPass123!", "NewPass456!");

        when(userRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("OldPass123!", "hash")).thenReturn(true);
        when(passwordEncoder.matches("NewPass456!", "hash")).thenReturn(false);
        when(passwordEncoder.encode("NewPass456!")).thenReturn("new-hash");
        when(userRepository.save(user)).thenReturn(user);

        userService.changePassword(user, request);

        assertEquals("new-hash", user.getPassword());
        assertEquals(1L, user.getAuthVersion());
        verify(refreshSessionRepository).revokeAllActiveForUser(
                eq(9L),
                eq("PASSWORD_CHANGED"),
                any(LocalDateTime.class)
        );
    }

    @Test
    void googleLoginCreatesOauthOnlyUserWalletAndProviderIdentity() {
        GoogleLoginRequest request = new GoogleLoginRequest("google-id-token");
        GoogleIdentity googleIdentity = new GoogleIdentity("google-subject-123", "Person@Gmail.com", "Google Person", true);

        when(googleIdentityVerifier.verify("google-id-token")).thenReturn(googleIdentity);
        when(authIdentityRepository.findByProviderAndProviderSubject(AuthProvider.GOOGLE, "google-subject-123")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("person@gmail.com")).thenReturn(Optional.empty());
        stubFederatedUserSave(22L);
        when(jwtUtil.generateToken("person@gmail.com", 0L)).thenReturn("google-session-token");
        when(jwtUtil.getExpirationMs()).thenReturn(3600000L);

        AuthResponse response = userService.loginWithGoogle(request);

        assertEquals("google-session-token", response.accessToken());
        assertEquals("person@gmail.com", response.user().email());
        verify(walletService).createWalletForUser(22L);
        verify(authIdentityRepository).save(any(UserAuthIdentity.class));
    }

    @Test
    void returningGoogleSubjectUsesLinkedUserWithoutEmailLookup() {
        GoogleLoginRequest request = new GoogleLoginRequest("google-id-token");
        GoogleIdentity googleIdentity = new GoogleIdentity("google-subject-123", "changed@gmail.com", "Google Person", true);
        User user = activeUser();
        UserAuthIdentity identity = linkedIdentity(user, AuthProvider.GOOGLE, "google-subject-123");

        when(googleIdentityVerifier.verify("google-id-token")).thenReturn(googleIdentity);
        when(authIdentityRepository.findByProviderAndProviderSubject(AuthProvider.GOOGLE, "google-subject-123")).thenReturn(Optional.of(identity));
        stubJwt(user);

        AuthResponse response = userService.loginWithGoogle(request);

        assertEquals("user@example.com", response.user().email());
        verify(userRepository, never()).findByEmailIgnoreCase("changed@gmail.com");
        verify(authIdentityRepository, never()).save(any(UserAuthIdentity.class));
    }

    @Test
    void googleDoesNotAutoLinkExistingThirdPartyEmailWhenGoogleIsNotAuthoritative() {
        GoogleLoginRequest request = new GoogleLoginRequest("google-id-token");
        GoogleIdentity googleIdentity = new GoogleIdentity("google-subject-123", "user@example.com", "Example User", false);
        User user = activeUser();

        when(googleIdentityVerifier.verify("google-id-token")).thenReturn(googleIdentity);
        when(authIdentityRepository.findByProviderAndProviderSubject(AuthProvider.GOOGLE, "google-subject-123")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));

        ConflictException exception = assertThrows(ConflictException.class, () -> userService.loginWithGoogle(request));
        assertFalse(exception.getMessage().isBlank());
        verify(authIdentityRepository, never()).save(any(UserAuthIdentity.class));
    }

    @Test
    void appleLoginCreatesFederatedUserWalletAndAppleIdentity() {
        AppleIdentity appleIdentity = new AppleIdentity("apple-subject-123", "private-relay@privaterelay.appleid.com", "Apple Person", true);

        when(authIdentityRepository.findByProviderAndProviderSubject(AuthProvider.APPLE, "apple-subject-123")).thenReturn(Optional.empty());
        when(userRepository.existsByEmailIgnoreCase("private-relay@privaterelay.appleid.com")).thenReturn(false);
        stubFederatedUserSave(31L);
        when(jwtUtil.generateToken("private-relay@privaterelay.appleid.com", 0L)).thenReturn("apple-session-token");
        when(jwtUtil.getExpirationMs()).thenReturn(3600000L);

        AuthResponse response = userService.loginWithAppleIdentity(appleIdentity);

        assertEquals("apple-session-token", response.accessToken());
        assertEquals("private-relay@privaterelay.appleid.com", response.user().email());
        verify(walletService).createWalletForUser(31L);

        ArgumentCaptor<UserAuthIdentity> identityCaptor = ArgumentCaptor.forClass(UserAuthIdentity.class);
        verify(authIdentityRepository).save(identityCaptor.capture());
        assertEquals(AuthProvider.APPLE, identityCaptor.getValue().getProvider());
        assertEquals("apple-subject-123", identityCaptor.getValue().getProviderSubject());
    }

    @Test
    void returningAppleSubjectDoesNotNeedEmailFromAppleAgain() {
        User user = activeUser();
        UserAuthIdentity linked = linkedIdentity(user, AuthProvider.APPLE, "apple-subject-123");
        when(authIdentityRepository.findByProviderAndProviderSubject(AuthProvider.APPLE, "apple-subject-123")).thenReturn(Optional.of(linked));
        stubJwt(user);

        AuthResponse response = userService.loginWithAppleIdentity(new AppleIdentity("apple-subject-123", null, null, false));

        assertEquals("user@example.com", response.user().email());
        verify(userRepository, never()).existsByEmailIgnoreCase(anyString());
    }

    @Test
    void appleNeverSilentlyLinksAnExistingAccountByEmail() {
        when(authIdentityRepository.findByProviderAndProviderSubject(AuthProvider.APPLE, "apple-subject-123")).thenReturn(Optional.empty());
        when(userRepository.existsByEmailIgnoreCase("user@example.com")).thenReturn(true);

        assertThrows(ConflictException.class, () -> userService.loginWithAppleIdentity(
                new AppleIdentity("apple-subject-123", "user@example.com", "Existing User", false)
        ));
        verify(authIdentityRepository, never()).save(any(UserAuthIdentity.class));
    }

    private void stubFederatedUserSave(long id) {
        when(passwordEncoder.encode(anyString())).thenReturn("random-password-hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", id);
            return saved;
        });
    }

    private void stubJwt(User user) {
        when(jwtUtil.generateToken(user.getEmail(), user.getAuthVersion())).thenReturn("token");
        when(jwtUtil.getExpirationMs()).thenReturn(3600000L);
    }

    private UserAuthIdentity linkedIdentity(User user, AuthProvider provider, String subject) {
        UserAuthIdentity identity = new UserAuthIdentity();
        identity.setUser(user);
        identity.setProvider(provider);
        identity.setProviderSubject(subject);
        return identity;
    }

    private User activeUser() {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", 9L);
        user.setEmail("user@example.com");
        user.setNickname("test-user");
        user.setPassword("hash");
        user.setPasswordLoginEnabled(true);
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
