package com.doFast.dofastapp.payout.service;

import com.doFast.dofastapp.payout.config.PayoutProperties;
import com.doFast.dofastapp.payout.entity.PayoutRequest;
import com.doFast.dofastapp.payout.enums.PayoutStatus;
import com.doFast.dofastapp.payout.repository.PayoutRequestRepository;
import com.doFast.dofastapp.user.entity.User;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayoutSubmittedReconciliationQueueTest {

    @Mock private PayoutRequestRepository payoutRepository;

    private PayoutSubmittedReconciliationQueue queue;

    @BeforeEach
    void setUp() {
        queue = new PayoutSubmittedReconciliationQueue(
                payoutRepository,
                new PayoutProperties("stripe-connect", false, new BigDecimal("1.00"), 5, 15, 300, 300)
        );
    }

    @Test
    void claimLeasesSubmittedPayoutWithoutCreatingAnotherDispatchAttempt() {
        PayoutRequest payout = submittedPayout();
        int dispatchAttempts = payout.getAttemptCount();
        LocalDateTime previousCheck = payout.getNextAttemptAt();
        when(payoutRepository.findNextSubmittedForReconciliationForUpdate(eq("stripe-connect"), any(LocalDateTime.class)))
                .thenReturn(Optional.of(payout));

        var command = queue.claimNext("stripe-connect").orElseThrow();

        assertEquals(41L, command.payoutId());
        assertEquals("po_123", command.providerReference());
        assertEquals("tr_123", command.providerTransferReference());
        assertEquals(dispatchAttempts, payout.getAttemptCount());
        assertEquals(PayoutStatus.SUBMITTED, payout.getStatus());
        assertTrue(payout.getNextAttemptAt().isAfter(previousCheck));
        assertTrue(payout.getNextAttemptAt().isAfter(LocalDateTime.now().plusSeconds(290)));
        verify(payoutRepository).saveAndFlush(payout);
    }

    @Test
    void providerFailureIsRecordedButReservationRemainsSubmitted() {
        PayoutRequest payout = submittedPayout();
        when(payoutRepository.findByIdForUpdate(41L)).thenReturn(Optional.of(payout));

        queue.recordProviderFailure(41L, "stripe reconciliation timeout");

        assertEquals(PayoutStatus.SUBMITTED, payout.getStatus());
        assertEquals("STRIPE_RECONCILIATION_TIMEOUT", payout.getFailureCode());
        assertTrue(payout.getLastErrorAt() != null);
        verify(payoutRepository).save(payout);
    }

    @Test
    void healthyProviderReadClearsOnlyTransientReconciliationFailure() {
        PayoutRequest payout = submittedPayout();
        payout.recordSubmittedReconciliationFailure("STRIPE_RECONCILIATION_ERROR", LocalDateTime.now());
        when(payoutRepository.findByIdForUpdate(41L)).thenReturn(Optional.of(payout));

        queue.recordProviderHealthy(41L);

        assertEquals(PayoutStatus.SUBMITTED, payout.getStatus());
        assertNull(payout.getFailureCode());
        verify(payoutRepository).save(payout);
    }

    private PayoutRequest submittedPayout() {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", 7L);
        PayoutRequest payout = new PayoutRequest();
        payout.initialize(
                user,
                "payout:7:client:test",
                new BigDecimal("125.00"),
                "PLN",
                "stripe-connect",
                LocalDateTime.now().minusHours(1)
        );
        ReflectionTestUtils.setField(payout, "id", 41L);
        payout.startProcessing(LocalDateTime.now().minusMinutes(20));
        payout.recordProviderTransferReference("tr_123");
        payout.markSubmitted("po_123", LocalDateTime.now().minusMinutes(20));
        ReflectionTestUtils.setField(payout, "nextAttemptAt", LocalDateTime.now().minusSeconds(1));
        return payout;
    }
}
