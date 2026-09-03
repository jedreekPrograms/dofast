package com.doFast.dofastapp.user.service;

import com.doFast.dofastapp.common.exception.AuthenticationFailedException;
import com.doFast.dofastapp.common.exception.BusinessException;
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
import com.doFast.dofastapp.wallet.service.WalletService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class UserService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String PASSWORD_CHANGED_SESSION_REASON = "PASSWORD_CHANGED";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final WalletService walletService;
    private final UserAuthIdentityRepository authIdentityRepository;
    private final GoogleIdentityVerifier googleIdentityVerifier;
    private final AuthRefreshSessionRepository refreshSessionRepository;
    private final EmailVerificationService emailVerificationService;

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtUtil jwtUtil,
            WalletService walletService,
            UserAuthIdentityRepository authIdentityRepository,
            GoogleIdentityVerifier googleIdentityVerifier,
            AuthRefreshSessionRepository refreshSessionRepository,
            EmailVerificationService emailVerificationService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.walletService = walletService;
        this.authIdentityRepository = authIdentityRepository;
        this.googleIdentityVerifier = googleIdentityVerifier;
        this.refreshSessionRepository = refreshSessionRepository;
        this.emailVerificationService = emailVerificationService;
    }

    @Transactional
    public UserResponse createUser(UserRequest request) {
        String email = normalizeEmail(request.getEmail());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new BusinessException("Email już istnieje");
        }

        User user = new User();
        user.setEmail(email);
        user.setNickname(request.getNickname().trim());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setPasswordLoginEnabled(true);
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);

        User saved = userRepository.save(user);
        walletService.createWalletForUser(saved.getId());
        emailVerificationService.initializeLocalAccount(saved);
        return toResponse(saved);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(request.getEmail()))
                .orElseThrow(() -> new AuthenticationFailedException("Nieprawidłowy email lub hasło"));

        if (!user.isPasswordLoginEnabled() || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new AuthenticationFailedException("Nieprawidłowy email lub hasło");
        }
        requireActive(user);
        if (emailVerificationService.required() && !user.isEmailVerified()) {
            throw new ForbiddenOperationException("Adres email wymaga weryfikacji");
        }
        return createAuthResponse(user);
    }

    @Transactional
    public AuthResponse loginWithGoogle(GoogleLoginRequest request) {
        GoogleIdentity googleIdentity = googleIdentityVerifier.verify(request.credential());

        User user = authIdentityRepository
                .findByProviderAndProviderSubject(AuthProvider.GOOGLE, googleIdentity.subject())
                .map(UserAuthIdentity::getUser)
                .orElseGet(() -> linkOrCreateGoogleUser(googleIdentity));

        requireActive(user);
        return createAuthResponse(user);
    }

    @Transactional
    public AuthResponse loginWithAppleIdentity(AppleIdentity appleIdentity) {
        User linkedUser = authIdentityRepository
                .findByProviderAndProviderSubject(AuthProvider.APPLE, appleIdentity.subject())
                .map(UserAuthIdentity::getUser)
                .orElse(null);

        if (linkedUser != null) {
            requireActive(linkedUser);
            return createAuthResponse(linkedUser);
        }

        String email = normalizeOptionalEmail(appleIdentity.email());
        if (email == null) {
            throw new BusinessException(
                    "Apple nie przekazało adresu email potrzebnego do utworzenia nowego konta doFast"
            );
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException(
                    "Konto doFast z tym adresem email już istnieje. Zaloguj się dotychczasową metodą, aby bezpiecznie połączyć Apple."
            );
        }

        User user = createFederatedUser(email, normalizeAppleNickname(appleIdentity));
        saveAuthIdentity(user, AuthProvider.APPLE, appleIdentity.subject(), email);
        return createAuthResponse(user);
    }

    public UserResponse getCurrentUser(User principal) {
        requireUserId(principal);
        return toResponse(principal);
    }

    @Transactional
    public UserResponse updateProfile(User principal, UpdateProfileRequest request) {
        Long principalId = requireUserId(principal);
        User user = userRepository.findById(principalId)
                .orElseThrow(() -> new BusinessException("Użytkownik nie istnieje"));
        user.setNickname(request.nickname().trim());
        user.setBio(normalizePublicProfileField(request.bio()));
        user.setPublicLocation(normalizePublicProfileField(request.publicLocation()));
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public void changePassword(User principal, ChangePasswordRequest request) {
        Long principalId = requireUserId(principal);
        User user = userRepository.findByIdForUpdate(principalId)
                .orElseThrow(() -> new BusinessException("Użytkownik nie istnieje"));

        if (!user.isPasswordLoginEnabled()) {
            throw new BusinessException("To konto nie ma jeszcze włączonego logowania hasłem");
        }
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new BusinessException("Aktualne hasło jest nieprawidłowe");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new BusinessException("Nowe hasło musi różnić się od aktualnego");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.incrementAuthVersion();
        userRepository.save(user);
        refreshSessionRepository.revokeAllActiveForUser(
                user.getId(),
                PASSWORD_CHANGED_SESSION_REASON,
                LocalDateTime.now()
        );
    }

    @Transactional
    public User ensureBootstrapAdmin(String emailValue, String passwordValue, String nicknameValue) {
        String email = normalizeEmail(emailValue);
        return userRepository.findByEmailIgnoreCase(email)
                .map(existing -> {
                    if (existing.getRole() != UserRole.ADMIN) {
                        throw new IllegalStateException(
                                "Admin bootstrap email is already registered as a non-admin account"
                        );
                    }
                    if (!existing.isEmailVerified()) existing.markEmailVerified(LocalDateTime.now());
                    return existing;
                })
                .orElseGet(() -> {
                    User admin = new User();
                    admin.setEmail(email);
                    admin.setNickname(normalizeAdminNickname(nicknameValue));
                    admin.setPassword(passwordEncoder.encode(passwordValue));
                    admin.setPasswordLoginEnabled(true);
                    admin.markEmailVerified(LocalDateTime.now());
                    admin.setRole(UserRole.ADMIN);
                    admin.setStatus(UserStatus.ACTIVE);
                    User saved = userRepository.save(admin);
                    walletService.createWalletForUser(saved.getId());
                    return saved;
                });
    }

    public UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getBio(),
                user.getPublicLocation(),
                user.getRole(),
                user.getStatus(),
                user.isEmailVerified(),
                user.getCreatedAt()
        );
    }

    private Long requireUserId(User user) {
        if (user == null || user.getId() == null) {
            throw new ForbiddenOperationException("Zaloguj się, aby zarządzać kontem");
        }
        return user.getId();
    }

    private User linkOrCreateGoogleUser(GoogleIdentity googleIdentity) {
        String email = normalizeEmail(googleIdentity.email());
        User user = userRepository.findByEmailIgnoreCase(email)
                .map(existing -> linkGoogleToExistingUser(existing, googleIdentity))
                .orElseGet(() -> createFederatedUser(email, normalizeGoogleNickname(googleIdentity)));

        saveAuthIdentity(user, AuthProvider.GOOGLE, googleIdentity.subject(), email);
        return user;
    }

    private User linkGoogleToExistingUser(User user, GoogleIdentity googleIdentity) {
        requireActive(user);
        if (!googleIdentity.authoritativeForEmail()) {
            throw new ConflictException(
                    "Konto z tym adresem email już istnieje. Zaloguj się hasłem, aby bezpiecznie połączyć Google."
            );
        }
        if (authIdentityRepository.existsByUser_IdAndProvider(user.getId(), AuthProvider.GOOGLE)) {
            throw new AuthenticationFailedException("To konto jest już połączone z innym kontem Google");
        }
        if (!user.isEmailVerified()) {
            user.markEmailVerified(LocalDateTime.now());
            userRepository.save(user);
        }
        return user;
    }

    private User createFederatedUser(String email, String nickname) {
        User user = new User();
        user.setEmail(email);
        user.setNickname(nickname);
        user.setPassword(passwordEncoder.encode(generateUnusablePassword()));
        user.setPasswordLoginEnabled(false);
        user.markEmailVerified(LocalDateTime.now());
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);

        User saved = userRepository.save(user);
        walletService.createWalletForUser(saved.getId());
        return saved;
    }

    private void saveAuthIdentity(User user, AuthProvider provider, String subject, String providerEmail) {
        UserAuthIdentity identity = new UserAuthIdentity();
        identity.setUser(user);
        identity.setProvider(provider);
        identity.setProviderSubject(subject);
        identity.setProviderEmail(providerEmail);
        authIdentityRepository.save(identity);
    }

    private AuthResponse createAuthResponse(User user) {
        String token = jwtUtil.generateToken(user.getEmail(), user.getAuthVersion());
        return new AuthResponse(token, "Bearer", jwtUtil.getExpirationMs(), toResponse(user));
    }

    private void requireActive(User user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ForbiddenOperationException("Konto jest obecnie zawieszone");
        }
    }

    private String normalizeEmail(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeOptionalEmail(String value) {
        if (value == null || value.isBlank()) return null;
        return normalizeEmail(value);
    }

    private String normalizePublicProfileField(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private String normalizeGoogleNickname(GoogleIdentity googleIdentity) {
        return normalizeFederatedNickname(googleIdentity.displayName(), googleIdentity.email());
    }

    private String normalizeAppleNickname(AppleIdentity appleIdentity) {
        return normalizeFederatedNickname(appleIdentity.displayName(), appleIdentity.email());
    }

    private String normalizeFederatedNickname(String displayName, String email) {
        String value = displayName;
        if (value == null || value.isBlank()) {
            String normalizedEmail = normalizeOptionalEmail(email);
            value = normalizedEmail == null ? "doFast user" : normalizedEmail.split("@", 2)[0];
        }
        String trimmed = value.trim();
        if (trimmed.length() < 3) {
            trimmed = "user-" + trimmed;
        }
        return trimmed.length() <= 80 ? trimmed : trimmed.substring(0, 80);
    }

    private String generateUnusablePassword() {
        byte[] random = new byte[48];
        SECURE_RANDOM.nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    private String normalizeAdminNickname(String value) {
        if (value == null || value.isBlank()) {
            return "doFast Admin";
        }
        String trimmed = value.trim();
        return trimmed.length() <= 80 ? trimmed : trimmed.substring(0, 80);
    }
}
