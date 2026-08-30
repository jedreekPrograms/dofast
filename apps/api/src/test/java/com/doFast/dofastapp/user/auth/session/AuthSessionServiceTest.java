package com.doFast.dofastapp.user.auth.session;

import com.doFast.dofastapp.common.exception.AuthenticationFailedException;
import com.doFast.dofastapp.common.util.JwtUtil;
import com.doFast.dofastapp.user.dto.AuthResponse;
import com.doFast.dofastapp.user.dto.UserResponse;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.enums.UserRole;
import com.doFast.dofastapp.user.enums.UserStatus;
import com.doFast.dofastapp.user.repository.UserRepository;
import com.doFast.dofastapp.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthSessionServiceTest {

    @Mock private AuthRefreshSessionRepository sessionRepository;
    @Mock private JwtUtil jwtUtil;
    @Mock private UserService userService;
    @Mock private UserRepository userRepository;

    private AuthSessionSecrets secrets;
    private AuthSessionService sessionService;
    private User user;
    private UserResponse userResponse;

    @BeforeEach
    void setUp() {
        secrets = new AuthSessionSecrets();
        AuthSessionProperties properties = new AuthSessionProperties(30, 15, 7, false, "Strict");
        sessionService = new AuthSessionService(
                sessionRepository,
                secrets,
                properties,
                jwtUtil,
                userService,
                userRepository
        );
        user = activeUser();
        userResponse = new UserResponse(
                9L,
                "user@example.com",
                "test-user",
                null,
                null,
                UserRole.USER,
                UserStatus.ACTIVE,
                LocalDateTime.now().minusDays(30)
        );
    }

    @Test
    void issuePersistsOnlyHashesAndReturnsOpaqueSecrets() {
        when(userRepository.findById(9L)).thenReturn(Optional.of(user));
        when(sessionRepository.saveAndFlush(any(AuthRefreshSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        AuthResponse authResponse = new AuthResponse("access-token", "Bearer", 600_000L, userResponse);

        AuthSessionGrant grant = sessionService.issue(authResponse);

        assertEquals(authResponse, grant.response());
        assertFalse(grant.refreshToken().isBlank());
        assertFalse(grant.csrfToken().isBlank());

        ArgumentCaptor<AuthRefreshSession> captor = ArgumentCaptor.forClass(AuthRefreshSession.class);
        verify(sessionRepository).saveAndFlush(captor.capture());
        AuthRefreshSession stored = captor.getValue();
        assertEquals(64, stored.getTokenHash().length());
        assertEquals(64, stored.getCsrfHash().length());
        assertNotEquals(grant.refreshToken(), stored.getTokenHash());
        assertNotEquals(grant.csrfToken(), stored.getCsrfHash());
    }

    @Test
    void refreshRotatesSessionWithinSameFamilyAndIssuesFreshAccessToken() {
        String rawRefresh = "refresh-one";
        String rawCsrf = "csrf-one";
        UUID familyId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now().minusMinutes(5);
        AuthRefreshSession current = AuthRefreshSession.create(
                user,
                familyId,
                secrets.hash(rawRefresh),
                secrets.hash(rawCsrf),
                createdAt,
                createdAt.plusDays(30)
        );

        when(sessionRepository.findByTokenHashForUpdate(secrets.hash(rawRefresh)))
                .thenReturn(Optional.of(current));
        when(sessionRepository.saveAndFlush(any(AuthRefreshSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtUtil.generateToken("user@example.com")).thenReturn("fresh-access");
        when(jwtUtil.getExpirationMs()).thenReturn(600_000L);
        when(userService.toResponse(user)).thenReturn(userResponse);

        AuthSessionGrant grant = sessionService.refresh(rawRefresh, rawCsrf);

        assertEquals("fresh-access", grant.response().accessToken());
        assertEquals("ROTATED", current.getRevocationReason());
        assertNotNull(current.getRevokedAt());
        assertNotNull(current.getLastUsedAt());

        ArgumentCaptor<AuthRefreshSession> successorCaptor = ArgumentCaptor.forClass(AuthRefreshSession.class);
        verify(sessionRepository).saveAndFlush(successorCaptor.capture());
        assertEquals(familyId, successorCaptor.getValue().getFamilyId());
        verify(sessionRepository).save(current);
    }

    @Test
    void replayOfRotatedTokenAfterGraceRevokesActiveFamily() {
        String rawRefresh = "old-refresh";
        String rawCsrf = "old-csrf";
        UUID familyId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now().minusDays(1);
        AuthRefreshSession oldSession = AuthRefreshSession.create(
                user,
                familyId,
                secrets.hash(rawRefresh),
                secrets.hash(rawCsrf),
                createdAt,
                createdAt.plusDays(30)
        );
        oldSession.revoke("ROTATED", LocalDateTime.now().minusMinutes(1));

        when(sessionRepository.findByTokenHashForUpdate(secrets.hash(rawRefresh)))
                .thenReturn(Optional.of(oldSession));

        assertThrows(
                AuthenticationFailedException.class,
                () -> sessionService.refresh(rawRefresh, rawCsrf)
        );

        verify(sessionRepository).revokeActiveFamily(
                eq(familyId),
                eq("REUSE_DETECTED"),
                any(LocalDateTime.class)
        );
    }

    private User activeUser() {
        User value = new User();
        ReflectionTestUtils.setField(value, "id", 9L);
        value.setEmail("user@example.com");
        value.setNickname("test-user");
        value.setPassword("hash");
        value.setPasswordLoginEnabled(true);
        value.setRole(UserRole.USER);
        value.setStatus(UserStatus.ACTIVE);
        return value;
    }
}
