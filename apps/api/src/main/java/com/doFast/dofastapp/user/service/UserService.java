package com.doFast.dofastapp.user.service;

import com.doFast.dofastapp.common.exception.BusinessException;
import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.common.util.JwtUtil;
import com.doFast.dofastapp.user.dto.AuthResponse;
import com.doFast.dofastapp.user.dto.ChangePasswordRequest;
import com.doFast.dofastapp.user.dto.LoginRequest;
import com.doFast.dofastapp.user.dto.UpdateProfileRequest;
import com.doFast.dofastapp.user.dto.UserRequest;
import com.doFast.dofastapp.user.dto.UserResponse;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.enums.UserRole;
import com.doFast.dofastapp.user.enums.UserStatus;
import com.doFast.dofastapp.user.repository.UserRepository;
import com.doFast.dofastapp.wallet.service.WalletService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final WalletService walletService;

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtUtil jwtUtil,
            WalletService walletService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.walletService = walletService;
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
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);

        User saved = userRepository.save(user);
        walletService.createWalletForUser(saved.getId());
        return toResponse(saved);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(request.getEmail()))
                .orElseThrow(() -> new BusinessException("Nieprawidłowy email lub hasło"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException("Nieprawidłowy email lub hasło");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ForbiddenOperationException("Konto jest obecnie zawieszone");
        }

        String token = jwtUtil.generateToken(user.getEmail());
        return new AuthResponse(token, "Bearer", jwtUtil.getExpirationMs(), toResponse(user));
    }

    public UserResponse getCurrentUser(User principal) {
        return toResponse(principal);
    }

    @Transactional
    public UserResponse updateProfile(User principal, UpdateProfileRequest request) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new BusinessException("Użytkownik nie istnieje"));
        user.setNickname(request.nickname().trim());
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public void changePassword(User principal, ChangePasswordRequest request) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new BusinessException("Użytkownik nie istnieje"));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new BusinessException("Aktualne hasło jest nieprawidłowe");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new BusinessException("Nowe hasło musi różnić się od aktualnego");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
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
                    return existing;
                })
                .orElseGet(() -> {
                    User admin = new User();
                    admin.setEmail(email);
                    admin.setNickname(normalizeAdminNickname(nicknameValue));
                    admin.setPassword(passwordEncoder.encode(passwordValue));
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
                user.getRole(),
                user.getStatus(),
                user.getCreatedAt()
        );
    }

    private String normalizeEmail(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeAdminNickname(String value) {
        if (value == null || value.isBlank()) {
            return "doFast Admin";
        }
        String trimmed = value.trim();
        return trimmed.length() <= 80 ? trimmed : trimmed.substring(0, 80);
    }
}
