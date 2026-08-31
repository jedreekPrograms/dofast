package com.doFast.dofastapp.payout.service;

import com.doFast.dofastapp.payout.config.PayoutProperties;
import com.doFast.dofastapp.payout.entity.PayoutRequest;
import com.doFast.dofastapp.payout.enums.PayoutStatus;
import com.doFast.dofastapp.payout.provider.PayoutDispatchResult;
import com.doFast.dofastapp.payout.repository.PayoutEventRepository;
import com.doFast.dofastapp.payout.repository.PayoutRequestRepository;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.enums.UserStatus;
import com.doFast.dofastapp.user.repository.UserRepository;
import com.doFast.dofastapp.verification.entity.VerificationCase;
import com.doFast.dofastapp.verification.repository.VerificationCaseRepository;
import com.doFast.dofastapp.wallet.enums.WalletTransactionType;
import com.doFast.dofastapp.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayoutDispatchQueueTest {

    @Mock private PayoutRequestRepository payoutRepository;
    @Mock private PayoutEventRepository eventRepository;
    @Mock private UserRepository userRepository;
    @Mock private VerificationCaseRepository verificationRepository;
    @Mock private WalletService walletService;

    private PayoutDispatchQueue queue;

    @BeforeEach
    void setUp() {
        queue = queueWithProperties(new PayoutProperties(
                "sandbox", true, new BigDecimal("1.00"), 1, 15, 300, 300
        ));
    }

    @Test
    void revokedVerificationMovesQueuedPayoutToManualReviewWithoutProviderDispatch() {
        User user = user(7L, UserStatus.ACTIVE);
        PayoutRequest payout = payout(41L, user);
        VerificationCase verification = new VerificationCase();
        verification.initialize(user, "manual", null, LocalDateTime.now().minusDays(1));

        when(payoutRepository.findNextDispatchableForUpdate(any(), any()))
                .thenReturn(Optional.of(payout), Optional.empty());
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        when(verificationRepository.findByUserIdForUpdate(7L)).thenReturn(Optional.of(verification));

        var command = queue.claimNext("sandbox");

        assertTrue(command.isEmpty());
        assertEquals(PayoutStatus.REVIEW_REQUIRED, payout.getStatus());
        verify(payoutRepository).saveAndFlush(payout);
        verify(eventRepository).save(any());
    }

    @Test
    void verifiedActivePayoutIsClaimedWithStableProviderIdempotencyKey() {
        User user = user(7L, UserStatus.ACTIVE);
        PayoutRequest payout = payout(41L, user);
        VerificationCase verified = verified(user);

        when(payoutRepository.findNextDispatchableForUpdate(any(), any())).thenReturn(Optional.of(payout));
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        when(verificationRepository.findByUserIdForUpdate(7L)).thenReturn(Optional.of(verified));

        var command = queue.claimNext("sandbox").orElseThrow();

        assertEquals(PayoutStatus.PROCESSING, payout.getStatus());
        assertEquals("payout:41:provider", command.idempotencyKey());
        assertEquals(1, command.attempt());
    }

    @Test
    void definitiveProviderFailureRestoresReservedSourcesExactlyOnce() {
        User user = user(7L, UserStatus.ACTIVE);
        PayoutRequest payout = payout(41L, user);
        payout.startProcessing(LocalDateTime.now());
        when(payoutRepository.findByIdForUpdate(41L)).thenReturn(Optional.of(payout));
        when(walletService.creditRestoringOperation(
                7L,
                new BigDecimal("25.00"),
                WalletTransactionType.PAYOUT_RESTORE,
                null,
                "payout:41:restore",
                "payout:7:client:req-12345:reserve"
        )).thenReturn(true);

        queue.complete(41L, PayoutDispatchResult.definitiveFailure("RECIPIENT_REJECTED"));

        assertEquals(PayoutStatus.FAILED, payout.getStatus());
        verify(walletService).creditRestoringOperation(
                7L,
                new BigDecimal("25.00"),
                WalletTransactionType.PAYOUT_RESTORE,
                null,
                "payout:41:restore",
                "payout:7:client:req-12345:reserve"
        );
    }

    @Test
    void ambiguousProviderFailureNeverRestoresFundsAndRequiresReviewAtRetryLimit() {
        User user = user(7L, UserStatus.ACTIVE);
        PayoutRequest payout = payout(41L, user);
        payout.startProcessing(LocalDateTime.now());
        when(payoutRepository.findByIdForUpdate(41L)).thenReturn(Optional.of(payout));

        queue.complete(41L, PayoutDispatchResult.retryableFailure("TIMEOUT"));

        assertEquals(PayoutStatus.REVIEW_REQUIRED, payout.getStatus());
        verify(walletService, never()).creditRestoringOperation(any(), any(), any(), any(), any(), any());
    }

    @Test
    void submittedProviderResultWaitsForSettlementAndSchedulesReadOnlyReconciliation() {
        User user = user(7L, UserStatus.ACTIVE);
        PayoutRequest payout = payout(41L, user);
        payout.startProcessing(LocalDateTime.now());
        int dispatchAttempts = payout.getAttemptCount();
        LocalDateTime before = LocalDateTime.now();
        when(payoutRepository.findByIdForUpdate(41L)).thenReturn(Optional.of(payout));

        queue.complete(41L, PayoutDispatchResult.submitted("provider-payout-41"));

        assertEquals(PayoutStatus.SUBMITTED, payout.getStatus());
        assertEquals("provider-payout-41", payout.getProviderReference());
        assertNotNull(payout.getProviderSubmittedAt());
        assertNotNull(payout.getNextAttemptAt());
        assertTrue(payout.getNextAttemptAt().isAfter(before.plusSeconds(290)));
        assertEquals(dispatchAttempts, payout.getAttemptCount());
        assertNull(payout.getResolvedAt());
        verify(walletService, never()).creditRestoringOperation(any(), any(), any(), any(), any(), any());
        verify(walletService, never()).debit(any(), any(), any(), any(), any());
    }

    @Test
    void successfulSynchronousProviderResultMarksPaidWithoutSecondWalletDebit() {
        User user = user(7L, UserStatus.ACTIVE);
        PayoutRequest payout = payout(41L, user);
        payout.startProcessing(LocalDateTime.now());
        when(payoutRepository.findByIdForUpdate(41L)).thenReturn(Optional.of(payout));

        queue.complete(41L, PayoutDispatchResult.success("sandbox-payout-41"));

        assertEquals(PayoutStatus.PAID, payout.getStatus());
        assertEquals("sandbox-payout-41", payout.getProviderReference());
        assertNull(payout.getProviderSubmittedAt());
        verify(walletService, never()).creditRestoringOperation(any(), any(), any(), any(), any(), any());
        verify(walletService, never()).debit(any(), any(), any(), any(), any());
    }

    @Test
    void recentAmbiguousStripeConnectDispatchStillRetriesWithStableProviderKey() {
        User user = user(7L, UserStatus.ACTIVE);
        PayoutRequest payout = payout(41L, user, "stripe-connect", LocalDateTime.now().minusMinutes(10));
        payout.startProcessing(LocalDateTime.now().minusMinutes(5));
        PayoutDispatchQueue retryingQueue = queueWithProperties(new PayoutProperties(
                "stripe-connect", false, new BigDecimal("1.00"), 5, 15, 300, 300
        ));
        when(payoutRepository.findStaleProcessingForUpdate(any())).thenReturn(Optional.of(payout));

        retryingQueue.recoverOneStaleProcessing();

        assertEquals(PayoutStatus.REQUESTED, payout.getStatus());
        assertEquals("STALE_PROCESSING", payout.getFailureCode());
        assertNotNull(payout.getNextAttemptAt());
        assertEquals(1, payout.getAttemptCount());
        verify(walletService, never()).creditRestoringOperation(any(), any(), any(), any(), any(), any());
    }

    @Test
    void expiredAmbiguousStripeConnectDispatchRequiresExternalReviewWithoutWalletRestore() {
        User user = user(7L, UserStatus.ACTIVE);
        PayoutRequest payout = payout(41L, user, "stripe-connect", LocalDateTime.now().minusHours(26));
        payout.startProcessing(LocalDateTime.now().minusHours(25));
        PayoutDispatchQueue retryingQueue = queueWithProperties(new PayoutProperties(
                "stripe-connect", false, new BigDecimal("1.00"), 5, 15, 300, 300
        ));
        when(payoutRepository.findStaleProcessingForUpdate(any())).thenReturn(Optional.of(payout));

        retryingQueue.recoverOneStaleProcessing();

        assertEquals(PayoutStatus.REVIEW_REQUIRED, payout.getStatus());
        assertEquals(PayoutProviderSafetyPolicy.STRIPE_CONNECT_IDEMPOTENCY_WINDOW_EXPIRED, payout.getFailureCode());
        assertNull(payout.getProcessingStartedAt());
        assertEquals(1, payout.getAttemptCount());
        verify(walletService, never()).creditRestoringOperation(any(), any(), any(), any(), any(), any());
    }

    private PayoutDispatchQueue queueWithProperties(PayoutProperties properties) {
        return new PayoutDispatchQueue(
                payoutRepository,
                eventRepository,
                userRepository,
                verificationRepository,
                walletService,
                properties
        );
    }

    private PayoutRequest payout(Long id, User user) {
        return payout(id, user, "sandbox", LocalDateTime.now().minusMinutes(1));
    }

    private PayoutRequest payout(Long id, User user, String providerCode, LocalDateTime requestedAt) {
        PayoutRequest payout = new PayoutRequest();
        payout.initialize(
                user,
                "payout:" + user.getId() + ":client:req-12345",
                new BigDecimal("25.00"),
                "PLN",
                providerCode,
                requestedAt
        );
        ReflectionTestUtils.setField(payout, "id", id);
        return payout;
    }

    private VerificationCase verified(User user) {
        VerificationCase verification = new VerificationCase();
        verification.initialize(user, "manual", null, LocalDateTime.now().minusDays(1));
        verification.approve(user(99L, UserStatus.ACTIVE), LocalDateTime.now().minusHours(1));
        return verification;
    }

    private User user(Long id, UserStatus status) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setEmail("user" + id + "@example.com");
        user.setNickname("user" + id);
        user.setPassword("hash");
        user.setStatus(status);
        return user;
    }
}
