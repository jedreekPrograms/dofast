package com.doFast.dofastapp.payment.refund.service;

import com.stripe.model.Refund;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StripeRefundGatewayResponseValidationTest {

    private final StripeRefundGateway gateway = new StripeRefundGateway();

    @Test
    void amountMismatchWithKnownProviderIdCarriesProviderIdentityForReview() {
        StripeRefundDispatchCommand command = command();
        Refund refund = validRefund();
        refund.setAmount(2400L);
        StripeRefundProviderResult result = result(refund);

        StripeRefundProviderResponseException exception = assertThrows(
                StripeRefundProviderResponseException.class,
                () -> gateway.validateKnownProviderResponse(refund, command, result)
        );

        assertSame(result, exception.providerResult());
        assertEquals("re_41", exception.providerResult().refundId());
        assertEquals("provider_amount_mismatch", exception.violationCode());
    }

    @Test
    void missingStatusWithKnownProviderIdIsNotAReplayableTransportFailure() {
        StripeRefundDispatchCommand command = command();
        Refund refund = validRefund();
        refund.setStatus(null);
        StripeRefundProviderResult result = result(refund);

        StripeRefundProviderResponseException exception = assertThrows(
                StripeRefundProviderResponseException.class,
                () -> gateway.validateKnownProviderResponse(refund, command, result)
        );

        assertEquals("re_41", exception.providerResult().refundId());
        assertEquals("provider_status_missing", exception.violationCode());
    }

    private StripeRefundDispatchCommand command() {
        return new StripeRefundDispatchCommand(
                41L,
                7L,
                "pi_41",
                new BigDecimal("25.00"),
                "PLN",
                1
        );
    }

    private Refund validRefund() {
        Refund refund = new Refund();
        refund.setId("re_41");
        refund.setPaymentIntent("pi_41");
        refund.setAmount(2500L);
        refund.setCurrency("pln");
        refund.setStatus("succeeded");
        return refund;
    }

    private StripeRefundProviderResult result(Refund refund) {
        return new StripeRefundProviderResult(
                refund.getId(),
                refund.getStatus(),
                refund.getFailureReason()
        );
    }
}
