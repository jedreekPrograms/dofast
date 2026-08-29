package com.doFast.dofastapp.payment.refund;

import com.doFast.dofastapp.payment.refund.entity.StripeRefundRequest;
import com.doFast.dofastapp.payment.refund.entity.StripeRefundStatus;
import com.doFast.dofastapp.payment.refund.repository.StripeRefundEventRepository;
import com.doFast.dofastapp.payment.refund.repository.StripeRefundRequestRepository;
import com.doFast.dofastapp.payment.refund.service.StripeRefundSettlementResult;
import com.doFast.dofastapp.payment.refund.service.StripeRefundSettlementService;
import com.doFast.dofastapp.wallet.enums.WalletTransactionType;
import com.doFast.dofastapp.wallet.service.WalletService;
import com.stripe.model.Refund;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StripeRefundSettlementServiceTest {

    @Test
    void failedProviderRefundRestoresTheExactReservationFunding() {
        StripeRefundRequestRepository requests = mock(StripeRefundRequestRepository.class);
        StripeRefundEventRepository events = mock(StripeRefundEventRepository.class);
        WalletService wallet = mock(WalletService.class);
        StripeRefundSettlementService service = new StripeRefundSettlementService(requests, events, wallet);

        StripeRefundRequest request = mock(StripeRefundRequest.class);
        when(request.getId()).thenReturn(1002L);
        when(request.getUserId()).thenReturn(7L);
        when(request.getStripePaymentIntentId()).thenReturn("pi_refund_failure_smoke");
        when(request.getAmount()).thenReturn(new BigDecimal("15.00"));
        when(request.getCurrency()).thenReturn("PLN");
        when(request.getStatus()).thenReturn(StripeRefundStatus.FAILED);
        when(request.isWalletRestored()).thenReturn(false);

        Refund refund = mock(Refund.class);
        when(refund.getId()).thenReturn("re_refund_failure_smoke");
        when(refund.getPaymentIntent()).thenReturn("pi_refund_failure_smoke");
        when(refund.getAmount()).thenReturn(1500L);
        when(refund.getCurrency()).thenReturn("pln");
        when(refund.getMetadata()).thenReturn(Map.of("userId", "7"));
        when(refund.getStatus()).thenReturn("failed");
        when(refund.getFailureReason()).thenReturn("declined");

        when(requests.findByStripeRefundId("re_refund_failure_smoke")).thenReturn(Optional.of(request));
        when(requests.findByIdForUpdate(1002L)).thenReturn(Optional.of(request));
        when(events.claim(
                eq("evt_refund_failed"),
                eq(1002L),
                eq("re_refund_failure_smoke"),
                eq(StripeRefundSettlementService.FAILED),
                eq(123L),
                any(LocalDateTime.class)
        )).thenReturn(1);

        StripeRefundSettlementResult result = service.process(
                refund,
                "evt_refund_failed",
                StripeRefundSettlementService.FAILED,
                123L
        );

        assertThat(result).isEqualTo(StripeRefundSettlementResult.APPLIED);
        verify(wallet).creditRestoringOperation(
                7L,
                new BigDecimal("15.00"),
                WalletTransactionType.STRIPE_REFUND_RESTORE,
                null,
                "stripe:refund:1002:restore",
                "stripe:refund:1002:reserve"
        );
        verify(request).markWalletRestored(any(LocalDateTime.class));
    }
}
