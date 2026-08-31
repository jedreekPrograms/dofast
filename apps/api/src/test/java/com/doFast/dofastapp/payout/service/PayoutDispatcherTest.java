package com.doFast.dofastapp.payout.service;

import com.doFast.dofastapp.payout.provider.PayoutDispatchCommand;
import com.doFast.dofastapp.payout.provider.PayoutDispatchResult;
import com.doFast.dofastapp.payout.provider.PayoutProvider;
import com.doFast.dofastapp.payout.provider.PayoutProviderRegistry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

class PayoutDispatcherTest {

    @Test
    void localCompletionFailureAfterProviderSuccessDoesNotStarveLaterPayoutsOrInventCompensation() {
        PayoutProviderRegistry registry = mock(PayoutProviderRegistry.class);
        PayoutDispatchQueue queue = mock(PayoutDispatchQueue.class);
        PayoutProvider provider = mock(PayoutProvider.class);
        PayoutDispatcher dispatcher = new PayoutDispatcher(registry, queue);

        PayoutDispatchCommand first = command(41L);
        PayoutDispatchCommand second = command(42L);
        PayoutDispatchResult firstResult = PayoutDispatchResult.submitted("po_41");
        PayoutDispatchResult secondResult = PayoutDispatchResult.submitted("po_42");

        when(registry.isConfiguredProviderAvailable()).thenReturn(true);
        when(registry.configuredProviderCode()).thenReturn("stripe-connect");
        when(registry.requireProvider("stripe-connect")).thenReturn(provider);
        when(queue.claimNext("stripe-connect"))
                .thenReturn(Optional.of(first), Optional.of(second), Optional.empty());
        when(provider.dispatch(first)).thenReturn(firstResult);
        when(provider.dispatch(second)).thenReturn(secondResult);
        doThrow(new IllegalStateException("simulated database failure"))
                .when(queue).complete(41L, firstResult);

        dispatcher.dispatch();

        verify(queue).recoverOneStaleProcessing();
        var providerOrder = inOrder(provider);
        providerOrder.verify(provider).dispatch(first);
        providerOrder.verify(provider).dispatch(second);
        verify(queue).complete(41L, firstResult);
        verify(queue).complete(42L, secondResult);
        verify(queue, never()).complete(41L, PayoutDispatchResult.retryableFailure("PROVIDER_EXCEPTION"));
    }

    private PayoutDispatchCommand command(Long payoutId) {
        return new PayoutDispatchCommand(
                payoutId,
                7L,
                new BigDecimal("25.00"),
                "PLN",
                "stripe-connect",
                "payout:" + payoutId + ":provider",
                1
        );
    }
}
