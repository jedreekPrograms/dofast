package com.doFast.dofastapp.payout.service;

import com.doFast.dofastapp.payout.config.PayoutProperties;
import com.doFast.dofastapp.payout.entity.PayoutRequest;
import com.doFast.dofastapp.payout.enums.PayoutStatus;
import com.doFast.dofastapp.payout.repository.PayoutEventRepository;
import com.doFast.dofastapp.payout.repository.PayoutRequestRepository;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.repository.UserRepository;
import com.doFast.dofastapp.verification.repository.VerificationCaseRepository;
import com.doFast.dofastapp.wallet.service.WalletService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PayoutDispatchQueueProviderResponseReviewTest {

    @Test
    void providerResponseAnomalyQuarantinesWithoutRestoringFundsOrSchedulingRetry() {
        Fixture fixture = fixture();

        fixture.queue().quarantineProviderResponse(
                41L,
                "tr_41",
                "po_41",
                "STRIPE_PAYOUT_AMOUNT_MISMATCH"
        );

        assertEquals(PayoutStatus.REVIEW_REQUIRED, fixture.payout().getStatus());
        assertEquals("tr_41", fixture.payout().getProviderTransferReference());
        assertEquals("po_41", fixture.payout().getProviderReference());
        assertEquals("STRIPE_PAYOUT_AMOUNT_MISMATCH", fixture.payout().getFailureCode());
        assertNull(fixture.payout().getResolvedAt());
        verify(fixture.payoutRepository()).saveAndFlush(fixture.payout());
        verify(fixture.eventRepository()).save(any());
        verifyNoInteractions(fixture.walletService());
    }

    @Test
    void webhookWinsRaceAndLateResponseDoesNotRegressTerminalState() {
        Fixture fixture = fixture();
        fixture.payout().markSubmitted("po_41", LocalDateTime.of(2026, 8, 31, 20, 2));
        fixture.payout().markSubmittedPaid(LocalDateTime.of(2026, 8, 31, 20, 3));

        fixture.queue().quarantineProviderResponse(
                41L,
                "tr_other",
                "po_other",
                "STRIPE_PAYOUT_AMOUNT_MISMATCH"
        );

        assertEquals(PayoutStatus.PAID, fixture.payout().getStatus());
        assertEquals("po_41", fixture.payout().getProviderReference());
        verify(fixture.payoutRepository(), never()).saveAndFlush(fixture.payout());
        verify(fixture.eventRepository(), never()).save(any());
        verifyNoInteractions(fixture.walletService());
    }

    private Fixture fixture() {
        PayoutRequestRepository payoutRepository = mock(PayoutRequestRepository.class);
        PayoutEventRepository eventRepository = mock(PayoutEventRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        VerificationCaseRepository verificationRepository = mock(VerificationCaseRepository.class);
        WalletService walletService = mock(WalletService.class);
        PayoutProperties properties = mock(PayoutProperties.class);
        PayoutDispatchQueue queue = new PayoutDispatchQueue(
                payoutRepository,
                eventRepository,
                userRepository,
                verificationRepository,
                walletService,
                properties
        );
        PayoutRequest payout = new PayoutRequest();
        payout.initialize(
                new User(),
                "payout-request-41",
                new BigDecimal("25.00"),
                "PLN",
                "stripe-connect",
                LocalDateTime.of(2026, 8, 31, 20, 0)
        );
        payout.startProcessing(LocalDateTime.of(2026, 8, 31, 20, 1));
        when(payoutRepository.findByIdForUpdate(41L)).thenReturn(Optional.of(payout));
        return new Fixture(queue, payout, payoutRepository, eventRepository, walletService);
    }

    private record Fixture(
            PayoutDispatchQueue queue,
            PayoutRequest payout,
            PayoutRequestRepository payoutRepository,
            PayoutEventRepository eventRepository,
            WalletService walletService
    ) {}
}
