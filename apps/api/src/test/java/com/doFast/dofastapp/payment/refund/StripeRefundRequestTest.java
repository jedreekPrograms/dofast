package com.doFast.dofastapp.payment.refund;

import com.doFast.dofastapp.payment.refund.entity.StripeRefundRequest;
import com.doFast.dofastapp.payment.refund.entity.StripeRefundStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class StripeRefundRequestTest {

    @Test
    void newerFailureCanReversePreviouslySucceededProviderState() {
        LocalDateTime now = LocalDateTime.now();
        StripeRefundRequest request = request(now);

        request.startDispatch(now.plusSeconds(1));
        request.recordSubmission("re_test", "succeeded", now.plusSeconds(2));
        request.applyProviderEvent("re_test", "failed", "declined", 200L, now.plusSeconds(3));

        assertThat(request.getStatus()).isEqualTo(StripeRefundStatus.FAILED);
        assertThat(request.getFailureReason()).isEqualTo("declined");
    }

    @Test
    void olderProviderEventCannotRegressARefund() {
        LocalDateTime now = LocalDateTime.now();
        StripeRefundRequest request = request(now);

        request.startDispatch(now.plusSeconds(1));
        request.recordSubmission("re_test", "pending", now.plusSeconds(2));
        request.applyProviderEvent("re_test", "succeeded", null, 200L, now.plusSeconds(3));
        boolean applied = request.applyProviderEvent("re_test", "failed", "unknown", 100L, now.plusSeconds(4));

        assertThat(applied).isFalse();
        assertThat(request.getStatus()).isEqualTo(StripeRefundStatus.SUCCEEDED);
        assertThat(request.getFailureReason()).isNull();
    }

    @Test
    void localCancellationIsOnlyAllowedBeforeAnyProviderAttempt() {
        LocalDateTime now = LocalDateTime.now();
        StripeRefundRequest request = request(now);

        request.cancelBeforeFirstDispatch("payment_disputed", now.plusSeconds(1));

        assertThat(request.getStatus()).isEqualTo(StripeRefundStatus.CANCELED);
        assertThat(request.getFailureReason()).isEqualTo("payment_disputed");
        assertThat(request.getAttemptCount()).isZero();
    }

    private StripeRefundRequest request(LocalDateTime now) {
        return StripeRefundRequest.create(
                7L,
                "pi_test",
                "refund-test",
                new BigDecimal("20.00"),
                "PLN",
                now
        );
    }
}
