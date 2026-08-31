package com.doFast.dofastapp.payout.service;

import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.payout.entity.PayoutRequest;
import com.doFast.dofastapp.payout.provider.PayoutProviderRegistry;
import com.doFast.dofastapp.payout.repository.PayoutEventRepository;
import com.doFast.dofastapp.payout.repository.PayoutRequestRepository;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.enums.UserRole;
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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminPayoutServiceSafetyTest {

    @Mock private PayoutRequestRepository payoutRepository;
    @Mock private PayoutEventRepository eventRepository;
    @Mock private WalletService walletService;
    @Mock private PayoutProviderRegistry providerRegistry;

    private AdminPayoutService service;
    private User admin;

    @BeforeEach
    void setUp() {
        service = new AdminPayoutService(payoutRepository, eventRepository, walletService, providerRegistry);
        admin = user(99L);
        admin.setRole(UserRole.ADMIN);
    }

    @Test
    void adminCannotRetryStripeDispatchAfterSafeIdempotencyWindowExpired() {
        PayoutRequest payout = expiredAmbiguousPayout();
        when(payoutRepository.findByIdForUpdate(41L)).thenReturn(Optional.of(payout));

        assertThrows(ConflictException.class, () -> service.retry(41L, admin));

        verify(payoutRepository, never()).save(payout);
        verifyNoInteractions(providerRegistry, walletService, eventRepository);
    }

    @Test
    void adminCannotRestoreWalletWhileExpiredStripeDispatchMayHaveMovedMoney() {
        PayoutRequest payout = expiredAmbiguousPayout();
        when(payoutRepository.findByIdForUpdate(41L)).thenReturn(Optional.of(payout));

        assertThrows(ConflictException.class, () -> service.failAndRestore(41L, "operator cannot prove provider failure", admin));

        verify(payoutRepository, never()).save(payout);
        verifyNoInteractions(walletService, eventRepository, providerRegistry);
    }

    private PayoutRequest expiredAmbiguousPayout() {
        User worker = user(7L);
        PayoutRequest payout = new PayoutRequest();
        payout.initialize(
                worker,
                "payout:7:client:expired",
                new BigDecimal("25.00"),
                "PLN",
                StripeConnectOnboardingService.PROVIDER_CODE,
                LocalDateTime.now().minusHours(26)
        );
        ReflectionTestUtils.setField(payout, "id", 41L);
        payout.startProcessing(LocalDateTime.now().minusHours(25));
        payout.requireReview(PayoutProviderSafetyPolicy.STRIPE_CONNECT_IDEMPOTENCY_WINDOW_EXPIRED, LocalDateTime.now());
        return payout;
    }

    private User user(Long id) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setEmail("user" + id + "@example.com");
        user.setNickname("user" + id);
        user.setPassword("hash");
        return user;
    }
}
