package com.doFast.dofastapp.payment.service;

import com.doFast.dofastapp.common.exception.BusinessException;
import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.payment.entity.PaymentTransaction;
import com.doFast.dofastapp.payment.repository.PaymentTransactionRepository;
import com.doFast.dofastapp.wallet.enums.WalletTransactionType;
import com.doFast.dofastapp.wallet.service.WalletService;
import com.stripe.model.PaymentIntent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StripePaymentServiceTest {

    @Mock private WalletService walletService;
    @Mock private PaymentTransactionRepository paymentTransactionRepository;

    private StripePaymentService stripePaymentService;

    @BeforeEach
    void setUp() {
        stripePaymentService = new StripePaymentService(walletService, paymentTransactionRepository);
    }

    @Test
    void paymentIntentCreationRejectsAmountsOutsideSupportedTopUpRange() {
        assertThrows(
                BusinessException.class,
                () -> stripePaymentService.createPaymentIntent(new BigDecimal("0.99"), 7L, "req_low")
        );
        assertThrows(
                BusinessException.class,
                () -> stripePaymentService.createPaymentIntent(new BigDecimal("10000.01"), 7L, "req_high")
        );
    }

    @Test
    void successfulPaymentIsClaimedBeforeWalletCredit() {
        PaymentIntent intent = succeededIntent("pi_123", 2500L, "pln", "7");
        when(paymentTransactionRepository.claimSuccessfulPayment(
                eq("pi_123"),
                eq("evt_123"),
                eq(7L),
                eq(new BigDecimal("25.00")),
                eq("PLN"),
                any(LocalDateTime.class)
        )).thenReturn(1);
        when(walletService.credit(
                7L,
                new BigDecimal("25.00"),
                WalletTransactionType.TOP_UP,
                null,
                "stripe:intent:pi_123"
        )).thenReturn(true);

        assertTrue(stripePaymentService.processSuccessfulPayment(intent, "evt_123"));

        verify(walletService).credit(
                7L,
                new BigDecimal("25.00"),
                WalletTransactionType.TOP_UP,
                null,
                "stripe:intent:pi_123"
        );
    }

    @Test
    void duplicatePaymentIntentDoesNotCreditWalletAgainWhenStoredDataMatches() {
        PaymentIntent intent = succeededIntent("pi_123", 2500L, "pln", "7");
        PaymentTransaction stored = storedPayment(7L, "25.00", "PLN");
        when(paymentTransactionRepository.claimSuccessfulPayment(
                eq("pi_123"),
                eq("evt_retry"),
                eq(7L),
                eq(new BigDecimal("25.00")),
                eq("PLN"),
                any(LocalDateTime.class)
        )).thenReturn(0);
        when(paymentTransactionRepository.findByStripePaymentIntentId("pi_123"))
                .thenReturn(Optional.of(stored));

        assertFalse(stripePaymentService.processSuccessfulPayment(intent, "evt_retry"));
        verify(walletService, never()).credit(
                eq(7L),
                any(BigDecimal.class),
                any(WalletTransactionType.class),
                eq(null),
                any(String.class)
        );
    }

    @Test
    void duplicatePaymentIntentWithDifferentStoredAmountIsConflict() {
        PaymentIntent intent = succeededIntent("pi_123", 2500L, "pln", "7");
        PaymentTransaction stored = storedPayment(7L, "24.00", "PLN");
        when(paymentTransactionRepository.claimSuccessfulPayment(
                eq("pi_123"),
                eq("evt_retry"),
                eq(7L),
                eq(new BigDecimal("25.00")),
                eq("PLN"),
                any(LocalDateTime.class)
        )).thenReturn(0);
        when(paymentTransactionRepository.findByStripePaymentIntentId("pi_123"))
                .thenReturn(Optional.of(stored));

        assertThrows(
                ConflictException.class,
                () -> stripePaymentService.processSuccessfulPayment(intent, "evt_retry")
        );
        verify(walletService, never()).credit(
                any(Long.class),
                any(BigDecimal.class),
                any(WalletTransactionType.class),
                eq(null),
                any(String.class)
        );
    }

    @Test
    void reusedStripeEventForAnotherIntentIsConflict() {
        PaymentIntent intent = succeededIntent("pi_new", 2500L, "pln", "7");
        when(paymentTransactionRepository.claimSuccessfulPayment(
                eq("pi_new"),
                eq("evt_taken"),
                eq(7L),
                eq(new BigDecimal("25.00")),
                eq("PLN"),
                any(LocalDateTime.class)
        )).thenReturn(0);
        when(paymentTransactionRepository.findByStripePaymentIntentId("pi_new"))
                .thenReturn(Optional.empty());
        when(paymentTransactionRepository.findByStripeEventId("evt_taken"))
                .thenReturn(Optional.of(mock(PaymentTransaction.class)));

        assertThrows(
                ConflictException.class,
                () -> stripePaymentService.processSuccessfulPayment(intent, "evt_taken")
        );
        verify(walletService, never()).credit(
                any(Long.class),
                any(BigDecimal.class),
                any(WalletTransactionType.class),
                eq(null),
                any(String.class)
        );
    }

    @Test
    void signedWebhookCannotCreditUnsupportedCurrency() {
        PaymentIntent intent = succeededIntent("pi_123", 2500L, "eur", "7");

        assertThrows(
                IllegalStateException.class,
                () -> stripePaymentService.processSuccessfulPayment(intent, "evt_123")
        );

        verify(paymentTransactionRepository, never()).claimSuccessfulPayment(
                any(String.class),
                any(String.class),
                any(Long.class),
                any(BigDecimal.class),
                any(String.class),
                any(LocalDateTime.class)
        );
    }

    @Test
    void signedWebhookCannotCreditAmountAboveSupportedTopUpRange() {
        PaymentIntent intent = succeededIntent("pi_oversized", 1_000_001L, "pln", "7");

        assertThrows(
                IllegalStateException.class,
                () -> stripePaymentService.processSuccessfulPayment(intent, "evt_oversized")
        );

        verify(paymentTransactionRepository, never()).claimSuccessfulPayment(
                any(String.class),
                any(String.class),
                any(Long.class),
                any(BigDecimal.class),
                any(String.class),
                any(LocalDateTime.class)
        );
        verify(walletService, never()).credit(
                any(Long.class),
                any(BigDecimal.class),
                any(WalletTransactionType.class),
                eq(null),
                any(String.class)
        );
    }

    private PaymentTransaction storedPayment(long userId, String amount, String currency) {
        PaymentTransaction payment = mock(PaymentTransaction.class);
        when(payment.getUserId()).thenReturn(userId);
        when(payment.getAmount()).thenReturn(new BigDecimal(amount));
        when(payment.getCurrency()).thenReturn(currency);
        return payment;
    }

    private PaymentIntent succeededIntent(String id, long amount, String currency, String userId) {
        PaymentIntent intent = new PaymentIntent();
        intent.setId(id);
        intent.setAmount(amount);
        intent.setCurrency(currency);
        intent.setStatus("succeeded");
        intent.setMetadata(Map.of(
                "userId", userId,
                "topUpRequestId", "legacy-" + id
        ));
        return intent;
    }
}
