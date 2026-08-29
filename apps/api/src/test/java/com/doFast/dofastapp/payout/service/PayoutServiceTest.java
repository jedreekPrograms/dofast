package com.doFast.dofastapp.payout.service;

import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.payout.config.PayoutProperties;
import com.doFast.dofastapp.payout.dto.CreatePayoutRequest;
import com.doFast.dofastapp.payout.entity.PayoutRequest;
import com.doFast.dofastapp.payout.enums.PayoutStatus;
import com.doFast.dofastapp.payout.provider.PayoutProviderRegistry;
import com.doFast.dofastapp.payout.repository.PayoutEventRepository;
import com.doFast.dofastapp.payout.repository.PayoutRequestRepository;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.enums.UserStatus;
import com.doFast.dofastapp.user.repository.UserRepository;
import com.doFast.dofastapp.verification.entity.VerificationCase;
import com.doFast.dofastapp.verification.enums.VerificationStatus;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayoutServiceTest {

    @Mock private PayoutRequestRepository payoutRepository;
    @Mock private PayoutEventRepository eventRepository;
    @Mock private UserRepository userRepository;
    @Mock private VerificationCaseRepository verificationRepository;
    @Mock private WalletService walletService;
    @Mock private PayoutProviderRegistry providerRegistry;
    @Mock private StripeConnectOnboardingService onboardingService;

    private PayoutService payoutService;

    @BeforeEach
    void setUp() {
        payoutService = new PayoutService(
                payoutRepository,
                eventRepository,
                userRepository,
                verificationRepository,
                walletService,
                new PayoutProperties("sandbox", true, new BigDecimal("1.00"), 5, 15, 300, 300),
                providerRegistry,
                onboardingService
        );
    }

    @Test
    void verifiedActiveUserReservesFundsWhenRequestingPayout() {
        User user = user(7L, UserStatus.ACTIVE);
        VerificationCase verified = verification(user, VerificationStatus.VERIFIED);
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        when(payoutRepository.findByRequestKey("payout:7:client:req-12345")).thenReturn(Optional.empty());
        when(providerRegistry.providerCodeForNewRequest()).thenReturn("sandbox");
        when(verificationRepository.findByUserIdForUpdate(7L)).thenReturn(Optional.of(verified));
        when(payoutRepository.saveAndFlush(any(PayoutRequest.class))).thenAnswer(invocation -> {
            PayoutRequest payout = invocation.getArgument(0);
            ReflectionTestUtils.setField(payout, "id", 41L);
            return payout;
        });
        when(walletService.debit(
                eq(7L),
                eq(new BigDecimal("25.00")),
                eq(WalletTransactionType.PAYOUT_RESERVE),
                eq(null),
                eq("payout:7:client:req-12345:reserve")
        )).thenReturn(true);

        var response = payoutService.request(
                new CreatePayoutRequest(new BigDecimal("25.00"), "req-12345"),
                user
        );

        assertEquals(41L, response.id());
        assertEquals(PayoutStatus.REQUESTED, response.status());
        assertEquals(new BigDecimal("25.00"), response.amount());
        assertTrue(response.cancellable());
        verify(eventRepository).save(any());
    }

    @Test
    void stripeConnectRecipientMustBeFreshlyReadyBeforeFundsAreReserved() {
        User user = user(7L, UserStatus.ACTIVE);
        VerificationCase verified = verification(user, VerificationStatus.VERIFIED);
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        when(payoutRepository.findByRequestKey("payout:7:client:req-12345")).thenReturn(Optional.empty());
        when(providerRegistry.providerCodeForNewRequest()).thenReturn(StripeConnectOnboardingService.PROVIDER_CODE);
        when(verificationRepository.findByUserIdForUpdate(7L)).thenReturn(Optional.of(verified));
        when(onboardingService.setupAvailable()).thenReturn(true);
        when(onboardingService.refreshAndIsRecipientReady(user)).thenReturn(false);

        assertThrows(
                ForbiddenOperationException.class,
                () -> payoutService.request(new CreatePayoutRequest(new BigDecimal("25.00"), "req-12345"), user)
        );

        verify(onboardingService).refreshAndIsRecipientReady(user);
        verify(walletService, never()).debit(any(), any(), any(), any(), any());
        verify(payoutRepository, never()).saveAndFlush(any());
    }

    @Test
    void stripeConnectKillSwitchMustBeEnabledBeforeFundsAreReserved() {
        User user = user(7L, UserStatus.ACTIVE);
        VerificationCase verified = verification(user, VerificationStatus.VERIFIED);
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        when(payoutRepository.findByRequestKey("payout:7:client:req-12345")).thenReturn(Optional.empty());
        when(providerRegistry.providerCodeForNewRequest()).thenReturn(StripeConnectOnboardingService.PROVIDER_CODE);
        when(verificationRepository.findByUserIdForUpdate(7L)).thenReturn(Optional.of(verified));
        when(onboardingService.setupAvailable()).thenReturn(false);

        assertThrows(
                ForbiddenOperationException.class,
                () -> payoutService.request(new CreatePayoutRequest(new BigDecimal("25.00"), "req-12345"), user)
        );

        verify(onboardingService, never()).refreshAndIsRecipientReady(any());
        verify(walletService, never()).debit(any(), any(), any(), any(), any());
    }

    @Test
    void unverifiedUserCannotReservePayoutFunds() {
        User user = user(7L, UserStatus.ACTIVE);
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        when(payoutRepository.findByRequestKey("payout:7:client:req-12345")).thenReturn(Optional.empty());
        when(providerRegistry.providerCodeForNewRequest()).thenReturn("sandbox");
        when(verificationRepository.findByUserIdForUpdate(7L)).thenReturn(Optional.empty());

        assertThrows(
                ForbiddenOperationException.class,
                () -> payoutService.request(new CreatePayoutRequest(new BigDecimal("25.00"), "req-12345"), user)
        );

        verify(walletService, never()).debit(any(), any(), any(), any(), any());
        verify(payoutRepository, never()).saveAndFlush(any());
    }

    @Test
    void repeatedClientRequestReturnsExistingPayoutWithoutSecondReservation() {
        User user = user(7L, UserStatus.ACTIVE);
        PayoutRequest existing = payout(41L, user, new BigDecimal("25.00"));
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        when(payoutRepository.findByRequestKey("payout:7:client:req-12345")).thenReturn(Optional.of(existing));

        var response = payoutService.request(
                new CreatePayoutRequest(new BigDecimal("25.00"), "req-12345"),
                user
        );

        assertEquals(41L, response.id());
        assertEquals(PayoutStatus.REQUESTED, response.status());
        verify(providerRegistry, never()).providerCodeForNewRequest();
        verify(walletService, never()).debit(any(), any(), any(), any(), any());
    }

    @Test
    void ownerCanCancelQueuedPayoutAndRestoreReservedSources() {
        User user = user(7L, UserStatus.ACTIVE);
        PayoutRequest payout = payout(41L, user, new BigDecimal("25.00"));
        when(payoutRepository.findByIdForUpdate(41L)).thenReturn(Optional.of(payout));
        when(walletService.creditRestoringOperation(
                7L,
                new BigDecimal("25.00"),
                WalletTransactionType.PAYOUT_RESTORE,
                null,
                "payout:41:restore",
                "payout:7:client:req-12345:reserve"
        )).thenReturn(true);

        var response = payoutService.cancel(41L, user);

        assertEquals(PayoutStatus.CANCELLED, response.status());
        assertFalse(response.cancellable());
        verify(walletService).creditRestoringOperation(
                7L,
                new BigDecimal("25.00"),
                WalletTransactionType.PAYOUT_RESTORE,
                null,
                "payout:41:restore",
                "payout:7:client:req-12345:reserve"
        );
        verify(eventRepository, org.mockito.Mockito.times(2)).save(any());
    }

    @Test
    void eligibilityUsesWithdrawableBalanceNotTotalWalletBalance() {
        User user = user(7L, UserStatus.ACTIVE);
        when(verificationRepository.existsByUser_IdAndStatus(7L, VerificationStatus.VERIFIED)).thenReturn(true);
        when(providerRegistry.isConfiguredProviderAvailable()).thenReturn(true);
        when(providerRegistry.configuredProviderCode()).thenReturn(StripeConnectOnboardingService.PROVIDER_CODE);
        when(providerRegistry.providerMode()).thenReturn("LIVE");
        when(onboardingService.isRecipientReady(7L)).thenReturn(true);
        when(onboardingService.setupAvailable()).thenReturn(true);
        when(walletService.getWithdrawableBalance(7L)).thenReturn(new BigDecimal("19.50"));

        var response = payoutService.eligibility(user);

        assertTrue(response.eligible());
        assertTrue(response.recipientReady());
        assertTrue(response.recipientSetupAvailable());
        assertEquals(new BigDecimal("19.50"), response.availableBalance());
        assertEquals("LIVE", response.providerMode());
        verify(walletService, never()).getMyWallet(7L);
    }

    private PayoutRequest payout(Long id, User user, BigDecimal amount) {
        PayoutRequest payout = new PayoutRequest();
        payout.initialize(user, "payout:" + user.getId() + ":client:req-12345", amount, "PLN", "sandbox", LocalDateTime.now());
        ReflectionTestUtils.setField(payout, "id", id);
        return payout;
    }

    private VerificationCase verification(User user, VerificationStatus status) {
        VerificationCase verification = new VerificationCase();
        verification.initialize(user, "manual", null, LocalDateTime.now().minusDays(1));
        if (status == VerificationStatus.VERIFIED) {
            verification.approve(user(99L, UserStatus.ACTIVE), LocalDateTime.now().minusHours(1));
        }
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
