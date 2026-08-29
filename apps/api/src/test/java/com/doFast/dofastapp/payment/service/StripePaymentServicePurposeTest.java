package com.doFast.dofastapp.payment.service;

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
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StripePaymentServicePurposeTest {

    @Mock private WalletService walletService;
    @Mock private PaymentTransactionRepository paymentTransactionRepository;

    private StripePaymentService service;

    @BeforeEach
    void setUp() {
        service = new StripePaymentService(walletService, paymentTransactionRepository);
    }

    @Test
    void rejectsPaymentIntentWithAnotherCommercialPurposeBeforeLedgerMutation() {
        PaymentIntent intent = paymentIntent(Map.of(
                "userId", "7",
                "purpose", StripePaymentService.JOB_PUBLICATION_PURPOSE,
                "jobPublicationId", "11",
                "topUpRequestId", "job-publication-11"
        ));

        assertThrows(IllegalStateException.class, () -> service.processSuccessfulPayment(intent, "evt_wrong-purpose"));

        verify(paymentTransactionRepository, never()).claimSuccessfulPayment(
                any(), any(), any(), any(), any(), any(), any(), any()
        );
        verify(walletService, never()).credit(any(), any(), any(), any(), any());
    }

    @Test
    void acceptsPublicationPurposeOnlyThroughScopedSettlement() {
        PaymentIntent intent = paymentIntent(Map.of(
                "userId", "7",
                "purpose", StripePaymentService.JOB_PUBLICATION_PURPOSE,
                "jobPublicationId", "11"
        ));
        when(paymentTransactionRepository.claimSuccessfulPayment(
                eq("pi_topup"), eq("evt_publication"), eq(7L), eq(new BigDecimal("25.00")), eq("PLN"),
                eq("JOB_PUBLICATION"), eq("11"), any()
        )).thenReturn(1);
        when(walletService.credit(
                7L, new BigDecimal("25.00"), WalletTransactionType.TOP_UP, null, "stripe:intent:pi_topup"
        )).thenReturn(true);

        assertTrue(service.processSuccessfulJobPublicationPayment(intent, "evt_publication", 11L));
    }

    @Test
    void scopedPublicationSettlementRejectsDifferentPublicationBeforeLedgerMutation() {
        PaymentIntent intent = paymentIntent(Map.of(
                "userId", "7",
                "purpose", StripePaymentService.JOB_PUBLICATION_PURPOSE,
                "jobPublicationId", "12"
        ));

        assertThrows(
                IllegalStateException.class,
                () -> service.processSuccessfulJobPublicationPayment(intent, "evt_publication", 11L)
        );
        verify(paymentTransactionRepository, never()).claimSuccessfulPayment(
                any(), any(), any(), any(), any(), any(), any(), any()
        );
        verify(walletService, never()).credit(any(), any(), any(), any(), any());
    }

    @Test
    void duplicateCannotChangeFromTopUpToPublicationSettlement() {
        PaymentIntent intent = paymentIntent(Map.of(
                "userId", "7",
                "purpose", StripePaymentService.JOB_PUBLICATION_PURPOSE,
                "jobPublicationId", "11"
        ));
        PaymentTransaction stored = storedPayment("TOP_UP", "request-original");
        when(paymentTransactionRepository.claimSuccessfulPayment(
                eq("pi_topup"), eq("evt_replay"), eq(7L), eq(new BigDecimal("25.00")), eq("PLN"),
                eq("JOB_PUBLICATION"), eq("11"), any()
        )).thenReturn(0);
        when(paymentTransactionRepository.findByStripePaymentIntentId("pi_topup"))
                .thenReturn(Optional.of(stored));

        assertThrows(
                ConflictException.class,
                () -> service.processSuccessfulJobPublicationPayment(intent, "evt_replay", 11L)
        );
        verify(walletService, never()).credit(any(), any(), any(), any(), any());
    }

    @Test
    void duplicatePublicationCannotBeReboundToAnotherPublication() {
        PaymentIntent intent = paymentIntent(Map.of(
                "userId", "7",
                "purpose", StripePaymentService.JOB_PUBLICATION_PURPOSE,
                "jobPublicationId", "12"
        ));
        PaymentTransaction stored = storedPayment("JOB_PUBLICATION", "11");
        when(paymentTransactionRepository.claimSuccessfulPayment(
                eq("pi_topup"), eq("evt_replay"), eq(7L), eq(new BigDecimal("25.00")), eq("PLN"),
                eq("JOB_PUBLICATION"), eq("12"), any()
        )).thenReturn(0);
        when(paymentTransactionRepository.findByStripePaymentIntentId("pi_topup"))
                .thenReturn(Optional.of(stored));

        assertThrows(
                ConflictException.class,
                () -> service.processSuccessfulJobPublicationPayment(intent, "evt_replay", 12L)
        );
        verify(walletService, never()).credit(any(), any(), any(), any(), any());
    }

    @Test
    void keepsLegacyTopUpWithoutPurposeCompatibleWhenItHasNoPublicationMarker() {
        PaymentIntent intent = paymentIntent(Map.of(
                "userId", "7",
                "topUpRequestId", "legacy-request-1"
        ));
        when(paymentTransactionRepository.claimSuccessfulPayment(
                eq("pi_topup"), eq("evt_legacy"), eq(7L), eq(new BigDecimal("25.00")), eq("PLN"),
                eq("TOP_UP"), eq("legacy-request-1"), any()
        )).thenReturn(1);
        when(walletService.credit(
                7L, new BigDecimal("25.00"), WalletTransactionType.TOP_UP, null, "stripe:intent:pi_topup"
        )).thenReturn(true);

        assertTrue(service.processSuccessfulPayment(intent, "evt_legacy"));
    }

    @Test
    void acceptsExplicitTopUpPurpose() {
        PaymentIntent intent = paymentIntent(Map.of(
                "userId", "7",
                "purpose", StripePaymentService.PURPOSE,
                "topUpRequestId", "request-2"
        ));
        when(paymentTransactionRepository.claimSuccessfulPayment(
                eq("pi_topup"), eq("evt_topup"), eq(7L), eq(new BigDecimal("25.00")), eq("PLN"),
                eq("TOP_UP"), eq("request-2"), any()
        )).thenReturn(1);
        when(walletService.credit(
                7L, new BigDecimal("25.00"), WalletTransactionType.TOP_UP, null, "stripe:intent:pi_topup"
        )).thenReturn(true);

        assertTrue(service.processSuccessfulPayment(intent, "evt_topup"));
    }

    private PaymentTransaction storedPayment(String purpose, String reference) {
        PaymentTransaction payment = mock(PaymentTransaction.class);
        when(payment.getUserId()).thenReturn(7L);
        when(payment.getAmount()).thenReturn(new BigDecimal("25.00"));
        when(payment.getCurrency()).thenReturn("PLN");
        when(payment.getSettlementPurpose()).thenReturn(purpose);
        when(payment.getBusinessReference()).thenReturn(reference);
        return payment;
    }

    private PaymentIntent paymentIntent(Map<String, String> metadata) {
        PaymentIntent intent = new PaymentIntent();
        intent.setId("pi_topup");
        intent.setAmount(2500L);
        intent.setCurrency("pln");
        intent.setStatus("succeeded");
        intent.setMetadata(metadata);
        return intent;
    }
}
