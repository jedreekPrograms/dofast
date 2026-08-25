package com.doFast.dofastapp.verification.service;

import com.doFast.dofastapp.common.exception.BusinessException;
import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.notification.enums.NotificationType;
import com.doFast.dofastapp.notification.service.NotificationService;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.enums.UserRole;
import com.doFast.dofastapp.user.enums.UserStatus;
import com.doFast.dofastapp.user.repository.UserRepository;
import com.doFast.dofastapp.verification.dto.AdminVerificationResponse;
import com.doFast.dofastapp.verification.dto.VerificationResponse;
import com.doFast.dofastapp.verification.entity.VerificationCase;
import com.doFast.dofastapp.verification.entity.VerificationEvent;
import com.doFast.dofastapp.verification.enums.VerificationDecision;
import com.doFast.dofastapp.verification.enums.VerificationStatus;
import com.doFast.dofastapp.verification.provider.ManualReviewVerificationProvider;
import com.doFast.dofastapp.verification.repository.VerificationCaseRepository;
import com.doFast.dofastapp.verification.repository.VerificationEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificationServiceTest {

    @Mock private VerificationCaseRepository verificationCaseRepository;
    @Mock private VerificationEventRepository verificationEventRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;

    private VerificationService verificationService;

    @BeforeEach
    void setUp() {
        verificationService = new VerificationService(
                verificationCaseRepository,
                verificationEventRepository,
                userRepository,
                notificationService,
                new ManualReviewVerificationProvider()
        );
    }

    @Test
    void firstRequestCreatesPendingCaseAndAuditEvent() {
        User user = user(7L, "user@example.com", "user");
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        when(verificationCaseRepository.findByUser_Id(7L)).thenReturn(Optional.empty());
        when(verificationCaseRepository.save(any(VerificationCase.class))).thenAnswer(invocation -> {
            VerificationCase saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 12L);
            return saved;
        });

        VerificationResponse response = verificationService.requestVerification(user);

        assertEquals(12L, response.id());
        assertEquals(VerificationStatus.PENDING, response.status());
        assertFalse(response.canRequest());
        verify(verificationEventRepository).save(any(VerificationEvent.class));
    }

    @Test
    void pendingRequestIsIdempotent() {
        User user = user(7L, "user@example.com", "user");
        VerificationCase pending = pendingCase(12L, user);
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        when(verificationCaseRepository.findByUser_Id(7L)).thenReturn(Optional.of(pending));

        VerificationResponse response = verificationService.requestVerification(user);

        assertEquals(VerificationStatus.PENDING, response.status());
        verify(verificationCaseRepository, never()).save(any(VerificationCase.class));
        verify(verificationEventRepository, never()).save(any(VerificationEvent.class));
    }

    @Test
    void rejectedVerificationCanBeResubmitted() {
        User user = user(7L, "user@example.com", "user");
        User reviewer = user(99L, "admin@example.com", "admin");
        VerificationCase rejected = pendingCase(12L, user);
        rejected.reject(reviewer, "Dokument wymaga ponownego sprawdzenia", LocalDateTime.now().minusDays(1));
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        when(verificationCaseRepository.findByUser_Id(7L)).thenReturn(Optional.of(rejected));
        when(verificationCaseRepository.save(rejected)).thenReturn(rejected);

        VerificationResponse response = verificationService.requestVerification(user);

        assertEquals(VerificationStatus.PENDING, response.status());
        assertEquals(null, response.decisionReason());
        assertFalse(response.canRequest());
        verify(verificationEventRepository).save(any(VerificationEvent.class));
    }

    @Test
    void verifiedIdentityCannotBeRequestedAgain() {
        User user = user(7L, "user@example.com", "user");
        User reviewer = user(99L, "admin@example.com", "admin");
        VerificationCase verified = pendingCase(12L, user);
        verified.approve(reviewer, LocalDateTime.now().minusDays(1));
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        when(verificationCaseRepository.findByUser_Id(7L)).thenReturn(Optional.of(verified));

        assertThrows(ConflictException.class, () -> verificationService.requestVerification(user));
    }

    @Test
    void adminApprovesPendingVerificationAndNotifiesUser() {
        User target = user(7L, "user@example.com", "user");
        User admin = user(99L, "admin@example.com", "admin");
        VerificationCase pending = pendingCase(12L, target);
        when(verificationCaseRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(pending));
        when(verificationCaseRepository.save(pending)).thenReturn(pending);

        AdminVerificationResponse response = verificationService.decide(
                12L,
                VerificationDecision.APPROVE,
                null,
                admin
        );

        assertEquals(VerificationStatus.VERIFIED, response.status());
        assertNotNull(response.verifiedAt());
        verify(notificationService).notify(
                eq(target),
                eq(NotificationType.VERIFICATION_APPROVED),
                any(String.class),
                any(String.class),
                eq(null),
                eq(null)
        );
        verify(verificationEventRepository).save(any(VerificationEvent.class));
    }

    @Test
    void rejectionRequiresMeaningfulReason() {
        User target = user(7L, "user@example.com", "user");
        User admin = user(99L, "admin@example.com", "admin");
        VerificationCase pending = pendingCase(12L, target);
        when(verificationCaseRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(pending));

        assertThrows(
                BusinessException.class,
                () -> verificationService.decide(12L, VerificationDecision.REJECT, "no", admin)
        );
        verify(notificationService, never()).notify(
                any(User.class),
                any(NotificationType.class),
                any(String.class),
                any(String.class),
                eq(null),
                eq(null)
        );
    }

    @Test
    void adminCannotReviewOwnVerification() {
        User admin = user(99L, "admin@example.com", "admin");
        VerificationCase pending = pendingCase(12L, admin);
        when(verificationCaseRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(pending));

        assertThrows(
                ForbiddenOperationException.class,
                () -> verificationService.decide(12L, VerificationDecision.APPROVE, null, admin)
        );
    }

    @Test
    void verifiedIdentityCanBeRevokedWithAuditReason() {
        User target = user(7L, "user@example.com", "user");
        User firstAdmin = user(98L, "first-admin@example.com", "firstAdmin");
        User admin = user(99L, "admin@example.com", "admin");
        VerificationCase verified = pendingCase(12L, target);
        verified.approve(firstAdmin, LocalDateTime.now().minusDays(2));
        when(verificationCaseRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(verified));
        when(verificationCaseRepository.save(verified)).thenReturn(verified);

        AdminVerificationResponse response = verificationService.decide(
                12L,
                VerificationDecision.REVOKE,
                "Weryfikacja została unieważniona po kontroli",
                admin
        );

        assertEquals(VerificationStatus.REVOKED, response.status());
        assertNotNull(response.revokedAt());
        assertEquals("Weryfikacja została unieważniona po kontroli", response.decisionReason());
        verify(notificationService).notify(
                eq(target),
                eq(NotificationType.VERIFICATION_REVOKED),
                any(String.class),
                any(String.class),
                eq(null),
                eq(null)
        );
    }

    private VerificationCase pendingCase(Long id, User user) {
        VerificationCase verification = new VerificationCase();
        verification.initialize(
                user,
                ManualReviewVerificationProvider.PROVIDER_CODE,
                null,
                LocalDateTime.now().minusHours(1)
        );
        ReflectionTestUtils.setField(verification, "id", id);
        return verification;
    }

    private User user(Long id, String email, String nickname) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setEmail(email);
        user.setNickname(nickname);
        user.setPassword("hash");
        user.setRole(email.contains("admin") ? UserRole.ADMIN : UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
