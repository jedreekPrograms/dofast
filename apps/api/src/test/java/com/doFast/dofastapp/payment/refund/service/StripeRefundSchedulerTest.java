package com.doFast.dofastapp.payment.refund.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StripeRefundSchedulerTest {

    @Test
    void staleRecoveryFailureDoesNotStarveCurrentDispatchableRefunds() {
        StripeRefundRequestService requestService = mock(StripeRefundRequestService.class);
        StripeRefundDispatchService dispatchService = mock(StripeRefundDispatchService.class);
        StripeRefundScheduler scheduler = new StripeRefundScheduler(requestService, dispatchService);

        doThrow(new IllegalStateException("simulated stale recovery failure"))
                .when(requestService).requeueStaleDispatches();
        when(requestService.findDispatchableIds(10)).thenReturn(List.of(21L, 22L));

        scheduler.dispatchPendingRefunds();

        verify(requestService).requeueStaleDispatches();
        var order = inOrder(dispatchService);
        order.verify(dispatchService).dispatch(21L);
        order.verify(dispatchService).dispatch(22L);
    }

    @Test
    void unexpectedFailureInOneRefundDoesNotStarveLaterDispatches() {
        StripeRefundRequestService requestService = mock(StripeRefundRequestService.class);
        StripeRefundDispatchService dispatchService = mock(StripeRefundDispatchService.class);
        StripeRefundScheduler scheduler = new StripeRefundScheduler(requestService, dispatchService);

        when(requestService.findDispatchableIds(10)).thenReturn(List.of(11L, 12L, 13L));
        doThrow(new IllegalStateException("simulated persistence failure"))
                .when(dispatchService).dispatch(11L);

        scheduler.dispatchPendingRefunds();

        verify(requestService).requeueStaleDispatches();
        var order = inOrder(dispatchService);
        order.verify(dispatchService).dispatch(11L);
        order.verify(dispatchService).dispatch(12L);
        order.verify(dispatchService).dispatch(13L);
    }
}
