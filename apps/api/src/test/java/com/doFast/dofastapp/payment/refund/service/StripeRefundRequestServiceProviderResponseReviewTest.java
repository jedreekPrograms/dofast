package com.doFast.dofastapp.payment.refund.service;

import com.doFast.dofastapp.payment.refund.entity.StripeRefundRequest;
import com.doFast.dofastapp.payment.refund.entity.StripeRefundStatus;
import com.doFast.dofastapp.payment.refund.repository.StripeRefundRequestRepository;
import com.doFast.dofastapp.payment.repository.PaymentTransactionRepository;
import com.doFast.dofastapp.payment.risk.repository.StripePaymentDisputeRepository;
import com.doFast.dofastapp.wallet.service.WalletService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StripeRefundRequestServiceProviderResponseReviewTest {

    @Test
    void recordingKnownProviderAnomalyDoesNotRestoreWallet() {
        StripeRefundRequestRepository refundRepository = mock(StripeRefundRequestRepository.class);
        PaymentTransactionRepository paymentRepository = mock(PaymentTransactionRepository.class);
        StripePaymentDisputeRepository disputeRepository = mock(StripePaymentDisputeRepository.class);
        WalletService walletService = mock(WalletService.class);
        StripeRefundRequestService service = new StripeRefundRequestService(
                refundRepository,
                paymentRepository,
                disputeRepository,
                walletService
        );
        LocalDateTime now = LocalDateTime.of(2026, 8, 31, 19, 0);
        StripeRefundRequest request = StripeRefundRequest.create(
                7L,
                "pi_41",
                "request-41",
                new BigDecimal("25.00"),
                "PLN",
                now
        );
        request.startDispatch(now.plusSeconds(1));
        when(refundRepository.findByIdForUpdate(41L)).thenReturn(Optional.of(request));
        StripeRefundProviderResult providerResult = new StripeRefundProviderResult(
                "re_41",
                "succeeded",
                null
        );

        service.recordProviderResponseForReview(
                41L,
                providerResult,
                "provider_amount_mismatch"
        );

        assertEquals(StripeRefundStatus.REVIEW_REQUIRED, request.getStatus());
        assertEquals("re_41", request.getStripeRefundId());
        assertFalse(request.isWalletRestored());
        verifyNoInteractions(walletService);
    }
}
