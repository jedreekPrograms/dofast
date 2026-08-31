package com.doFast.dofastapp.payment.refund;

import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.payment.entity.PaymentTransaction;
import com.doFast.dofastapp.payment.refund.dto.CreateStripeRefundRequest;
import com.doFast.dofastapp.payment.refund.entity.StripeRefundRequest;
import com.doFast.dofastapp.payment.refund.entity.StripeRefundStatus;
import com.doFast.dofastapp.payment.refund.repository.StripeRefundRequestRepository;
import com.doFast.dofastapp.payment.refund.service.StripeRefundRequestService;
import com.doFast.dofastapp.payment.repository.PaymentTransactionRepository;
import com.doFast.dofastapp.payment.risk.entity.StripePaymentDispute;
import com.doFast.dofastapp.payment.risk.repository.StripePaymentDisputeRepository;
import com.doFast.dofastapp.wallet.service.WalletService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StripeRefundRequestServiceTest {

    @Test
    void paymentThatEverEnteredStripeDisputeCannotBeRefundedAgain() {
        StripeRefundRequestRepository refunds = mock(StripeRefundRequestRepository.class);
        PaymentTransactionRepository payments = mock(PaymentTransactionRepository.class);
        StripePaymentDisputeRepository disputes = mock(StripePaymentDisputeRepository.class);
        WalletService wallet = mock(WalletService.class);
        StripeRefundRequestService service = new StripeRefundRequestService(refunds, payments, disputes, wallet);

        PaymentTransaction payment = mock(PaymentTransaction.class);
        when(refunds.findByUserIdAndRequestKey(7L, "refund-1")).thenReturn(Optional.empty());
        when(payments.findByStripePaymentIntentIdForUpdate("pi_test")).thenReturn(Optional.of(payment));
        when(payment.getUserId()).thenReturn(7L);
        when(payment.getCurrency()).thenReturn("PLN");
        when(payment.getSettlementPurpose()).thenReturn("TOP_UP");
        when(disputes.findByStripePaymentIntentId("pi_test")).thenReturn(Optional.of(mock(StripePaymentDispute.class)));

        assertThatThrownBy(() -> service.create(
                7L,
                new CreateStripeRefundRequest("refund-1", "pi_test", new BigDecimal("10.00"))
        )).isInstanceOf(ConflictException.class)
                .hasMessageContaining("dispute Stripe");

        verifyNoInteractions(wallet);
    }

    @Test
    void staleAmbiguousDispatchInsideStripeIdempotencyWindowIsRequeued() {
        StripeRefundRequestRepository refunds = mock(StripeRefundRequestRepository.class);
        PaymentTransactionRepository payments = mock(PaymentTransactionRepository.class);
        StripePaymentDisputeRepository disputes = mock(StripePaymentDisputeRepository.class);
        WalletService wallet = mock(WalletService.class);
        StripeRefundRequestService service = new StripeRefundRequestService(refunds, payments, disputes, wallet);

        LocalDateTime dispatchStartedAt = LocalDateTime.now().minusMinutes(5);
        StripeRefundRequest request = StripeRefundRequest.create(
                7L,
                "pi_test",
                "refund-safe-retry",
                new BigDecimal("10.00"),
                "PLN",
                dispatchStartedAt.minusMinutes(1)
        );
        request.startDispatch(dispatchStartedAt);
        when(refunds.findStaleDispatchesForUpdate(any(LocalDateTime.class), eq(100)))
                .thenReturn(List.of(request));

        int recovered = service.requeueStaleDispatches();

        assertThat(recovered).isEqualTo(1);
        assertThat(request.getStatus()).isEqualTo(StripeRefundStatus.REQUESTED);
        assertThat(request.getFailureReason()).isEqualTo("stale_dispatch_recovered");
        assertThat(request.getNextAttemptAt()).isNotNull();
        verify(refunds).saveAll(List.of(request));
        verifyNoInteractions(wallet);
    }

    @Test
    void staleAmbiguousDispatchPastStripeIdempotencyWindowRequiresReview() {
        StripeRefundRequestRepository refunds = mock(StripeRefundRequestRepository.class);
        PaymentTransactionRepository payments = mock(PaymentTransactionRepository.class);
        StripePaymentDisputeRepository disputes = mock(StripePaymentDisputeRepository.class);
        WalletService wallet = mock(WalletService.class);
        StripeRefundRequestService service = new StripeRefundRequestService(refunds, payments, disputes, wallet);

        LocalDateTime dispatchStartedAt = LocalDateTime.now().minusHours(25);
        StripeRefundRequest request = StripeRefundRequest.create(
                7L,
                "pi_test",
                "refund-expired-retry",
                new BigDecimal("10.00"),
                "PLN",
                dispatchStartedAt.minusMinutes(1)
        );
        request.startDispatch(dispatchStartedAt);
        when(refunds.findStaleDispatchesForUpdate(any(LocalDateTime.class), eq(100)))
                .thenReturn(List.of(request));

        int recovered = service.requeueStaleDispatches();

        assertThat(recovered).isEqualTo(1);
        assertThat(request.getStatus()).isEqualTo(StripeRefundStatus.REVIEW_REQUIRED);
        assertThat(request.getFailureReason()).isEqualTo("provider_idempotency_window_expired");
        assertThat(request.getNextAttemptAt()).isNull();
        assertThat(request.isWalletRestored()).isFalse();
        verify(refunds).saveAll(List.of(request));
        verifyNoInteractions(wallet);
    }
}
