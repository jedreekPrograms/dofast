package com.doFast.dofastapp.payment.refund.service;

import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.payment.refund.dto.CreateStripeRefundRequest;
import com.doFast.dofastapp.payment.refund.dto.StripeRefundResponse;
import com.doFast.dofastapp.payment.refund.entity.StripeRefundStatus;
import com.doFast.dofastapp.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StripeRefundCoordinatorTest {

    @Mock private StripeRefundRequestService requestService;
    @Mock private StripeRefundDispatchService dispatchService;

    @Test
    void refundOperationsRejectMissingOrTransientIdentityBeforePersistenceOrDispatch() {
        StripeRefundCoordinator coordinator = new StripeRefundCoordinator(requestService, dispatchService);
        CreateStripeRefundRequest request = new CreateStripeRefundRequest(
                "refund-1",
                "pi_private",
                new BigDecimal("5.00")
        );
        User transientUser = new User("transient@example.com", "transient");

        assertThrows(ForbiddenOperationException.class, () -> coordinator.request(null, request));
        assertThrows(ForbiddenOperationException.class, () -> coordinator.request(transientUser, request));
        assertThrows(ForbiddenOperationException.class, () -> coordinator.get(101L, null));
        assertThrows(ForbiddenOperationException.class, () -> coordinator.get(101L, transientUser));

        verifyNoInteractions(requestService, dispatchService);
    }

    @Test
    void persistedActorIdIsKeptAcrossCreationDispatchAndScopedRead() {
        StripeRefundCoordinator coordinator = new StripeRefundCoordinator(requestService, dispatchService);
        CreateStripeRefundRequest request = new CreateStripeRefundRequest(
                "refund-1",
                "pi_owner",
                new BigDecimal("5.00")
        );
        User user = new User("owner@example.com", "owner");
        ReflectionTestUtils.setField(user, "id", 7L);
        StripeRefundResponse response = new StripeRefundResponse(
                101L,
                "pi_owner",
                new BigDecimal("5.00"),
                "PLN",
                StripeRefundStatus.REQUESTED,
                null,
                null,
                0,
                null,
                null,
                null
        );
        when(requestService.create(7L, request)).thenReturn(response);
        when(requestService.get(101L, 7L)).thenReturn(response);

        assertSame(response, coordinator.request(user, request));

        verify(requestService).create(7L, request);
        verify(dispatchService).dispatch(101L);
        verify(requestService).get(101L, 7L);
    }
}
