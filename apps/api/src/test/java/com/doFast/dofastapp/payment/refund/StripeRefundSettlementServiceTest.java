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

    @Test
    void succeededRefundCannotRegressOnLaterProviderEvent() {
        StripeRefundRequest request = StripeRefundRequest.create(
                7L,
                "pi_refund_terminal",
                "terminal-ordering",
                new BigDecimal("15.00"),
                "PLN",
                LocalDateTime.parse("2026-08-31T01:00:00")
        );

        boolean succeeded = request.applyProviderEvent(
                "re_terminal",
                "succeeded",
                null,
                200L,
                LocalDateTime.parse("2026-08-31T01:01:00")
        );
        boolean regressed = request.applyProviderEvent(
                "re_terminal",
                "pending",
                null,
                201L,
                LocalDateTime.parse("2026-08-31T01:02:00")
        );

        assertThat(succeeded).isTrue();
        assertThat(regressed).isFalse();
        assertThat(request.getStatus()).isEqualTo(StripeRefundStatus.SUCCEEDED);
        assertThat(request.getStripeStatus()).isEqualTo("succeeded");
        assertThat(request.getProviderEventCreatedAt()).isEqualTo(200L);
    }

    @Test
    void terminalRefundCannotFlipOnSameSecondConflictingEvent() {
        StripeRefundRequest request = StripeRefundRequest.create(
                7L,
                "pi_refund_same_second",
                "same-second-ordering",
                new BigDecimal("15.00"),
                "PLN",
                LocalDateTime.parse("2026-08-31T01:00:00")
        );

        assertThat(request.applyProviderEvent(
                "re_same_second",
                "failed",
                "declined",
                300L,
                LocalDateTime.parse("2026-08-31T01:01:00")
        )).isTrue();

        assertThat(request.applyProviderEvent(
                "re_same_second",
                "succeeded",
                null,
                300L,
                LocalDateTime.parse("2026-08-31T01:01:01")
        )).isFalse();

        assertThat(request.getStatus()).isEqualTo(StripeRefundStatus.FAILED);
        assertThat(request.getStripeStatus()).isEqualTo("failed");
        assertThat(request.getFailureReason()).isEqualTo("declined");
        assertThat(request.getProviderEventCreatedAt()).isEqualTo(300L);
    }

    @Test
    void staleEventCannotMutateTerminalProviderMetadata() {
        StripeRefundRequest request = StripeRefundRequest.create(
                7L,
                "pi_refund_stale",
                "stale-ordering",
                new BigDecimal("15.00"),
                "PLN",
                LocalDateTime.parse("2026-08-31T01:00:00")
        );

        assertThat(request.applyProviderEvent(
                "re_stale",
                "canceled",
                "requested_by_customer",
                500L,
                LocalDateTime.parse("2026-08-31T01:01:00")
        )).isTrue();

        assertThat(request.applyProviderEvent(
                "re_stale",
                "pending",
                "temporary",
                499L,
                LocalDateTime.parse("2026-08-31T01:02:00")
        )).isFalse();

        assertThat(request.getStatus()).isEqualTo(StripeRefundStatus.CANCELED);
        assertThat(request.getStripeStatus()).isEqualTo("canceled");
        assertThat(request.getFailureReason()).isEqualTo("requested_by_customer");
        assertThat(request.getProviderEventCreatedAt()).isEqualTo(500L);
    }
}
