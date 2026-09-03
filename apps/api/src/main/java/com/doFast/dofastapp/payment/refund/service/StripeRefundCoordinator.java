package com.doFast.dofastapp.payment.refund.service;

import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.payment.refund.dto.CreateStripeRefundRequest;
import com.doFast.dofastapp.payment.refund.dto.StripeRefundResponse;
import com.doFast.dofastapp.user.entity.User;
import org.springframework.stereotype.Service;

@Service
public class StripeRefundCoordinator {

    private final StripeRefundRequestService requestService;
    private final StripeRefundDispatchService dispatchService;

    public StripeRefundCoordinator(
            StripeRefundRequestService requestService,
            StripeRefundDispatchService dispatchService
    ) {
        this.requestService = requestService;
        this.dispatchService = dispatchService;
    }

    public StripeRefundResponse request(User currentUser, CreateStripeRefundRequest input) {
        Long userId = requireUserId(currentUser);
        StripeRefundResponse created = requestService.create(userId, input);
        dispatchService.dispatch(created.id());
        return requestService.get(created.id(), userId);
    }

    public StripeRefundResponse get(Long requestId, User currentUser) {
        return requestService.get(requestId, requireUserId(currentUser));
    }

    private Long requireUserId(User user) {
        if (user == null || user.getId() == null) {
            throw new ForbiddenOperationException("Zaloguj się, aby zarządzać zwrotami");
        }
        return user.getId();
    }
}
