package com.doFast.dofastapp.payment.refund.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StripeRefundDispatchServiceTest {

    @Test
    void localPersistenceFailureAfterProviderSuccessIsNotReclassifiedAsProviderFailure() {
        StripeRefundRequestService requestService = mock(StripeRefundRequestService.class);
        StripeRefundGateway gateway = mock(StripeRefundGateway.class);
        StripeRefundDispatchService dispatchService = new StripeRefundDispatchService(requestService, gateway);

        StripeRefundDispatchCommand command = command();
        StripeRefundProviderResult providerResult = providerResult();

        when(requestService.claimForDispatch(41L)).thenReturn(command);
        when(gateway.create(command)).thenReturn(providerResult);
        doThrow(new IllegalStateException("simulated database failure"))
                .when(requestService).recordProviderResult(41L, providerResult);

        assertThrows(IllegalStateException.class, () -> dispatchService.dispatch(41L));

        verify(gateway).create(command);
        verify(requestService).recordProviderResult(41L, providerResult);
        verify(requestService, never()).recordDispatchFailure(41L);
        verify(requestService, never()).recordProviderResponseForReview(41L, providerResult, "provider_amount_mismatch");
    }

    @Test
    void knownProviderResponseMismatchIsQuarantinedWithoutAnotherProviderRetry() {
        StripeRefundRequestService requestService = mock(StripeRefundRequestService.class);
        StripeRefundGateway gateway = mock(StripeRefundGateway.class);
        StripeRefundDispatchService dispatchService = new StripeRefundDispatchService(requestService, gateway);

        StripeRefundDispatchCommand command = command();
        StripeRefundProviderResult providerResult = providerResult();
        StripeRefundProviderResponseException responseException = new StripeRefundProviderResponseException(
                "Stripe refund does not match the requested amount",
                providerResult,
                "provider_amount_mismatch"
        );

        when(requestService.claimForDispatch(41L)).thenReturn(command);
        when(gateway.create(command)).thenThrow(responseException);

        dispatchService.dispatch(41L);

        verify(gateway).create(command);
        verify(requestService).recordProviderResponseForReview(
                41L,
                providerResult,
                "provider_amount_mismatch"
        );
        verify(requestService, never()).recordDispatchFailure(41L);
        verify(requestService, never()).recordProviderResult(41L, providerResult);
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

    private StripeRefundProviderResult providerResult() {
        return new StripeRefundProviderResult(
                "re_41",
                "succeeded",
                null
        );
    }
}
