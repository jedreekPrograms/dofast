package com.doFast.dofastapp.user;

import com.doFast.dofastapp.common.exception.AuthenticationFailedException;
import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.common.util.JwtUtil;
import com.doFast.dofastapp.user.dto.AuthResponse;
import com.doFast.dofastapp.user.dto.LoginRequest;
import com.doFast.dofastapp.user.dto.UserRequest;
import com.doFast.dofastapp.user.dto.UserResponse;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.enums.UserRole;
import com.doFast.dofastapp.user.enums.UserStatus;
import com.doFast.dofastapp.user.repository.UserRepository;
import com.doFast.dofastapp.user.service.UserService;
import com.doFast.dofastapp.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @Mock private WalletService walletService;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder, jwtUtil, walletService);
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
    void successfulLoginReturnsBearerTokenAndCurrentRole() {
        LoginRequest request = new LoginRequest();
        request.setEmail("user@example.com");
        request.setPassword("StrongPass123!");

        User user = activeUser();
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("StrongPass123!", "hash")).thenReturn(true);
        when(jwtUtil.generateToken("user@example.com")).thenReturn("token");
        when(jwtUtil.getExpirationMs()).thenReturn(3600000L);

        AuthResponse response = userService.login(request);

        assertEquals("token", response.accessToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(UserRole.USER, response.user().role());
    }

    private User activeUser() {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", 9L);
        user.setEmail("user@example.com");
        user.setNickname("test-user");
        user.setPassword("hash");
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
