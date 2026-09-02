package com.doFast.dofastapp.payment.controller;

import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.payment.dto.CreatePaymentIntentRequest;
import com.doFast.dofastapp.payment.fee.PlatformFeePolicy;
import com.doFast.dofastapp.payment.fee.PlatformFeeQuoteService;
import com.doFast.dofastapp.payment.refund.dto.CreateStripeRefundRequest;
import com.doFast.dofastapp.payment.refund.service.StripeRefundCoordinator;
import com.doFast.dofastapp.payment.service.StripePaymentService;
import com.doFast.dofastapp.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    @Mock private StripePaymentService stripePaymentService;
    @Mock private PlatformFeePolicy platformFeePolicy;
    @Mock private PlatformFeeQuoteService platformFeeQuoteService;
    @Mock private StripeRefundCoordinator refundCoordinator;

    @Test
    void rejectsMissingPrincipalBeforeCallingFinancialServices() {
        PaymentController controller = controller();
        CreatePaymentIntentRequest intent = new CreatePaymentIntentRequest(new BigDecimal("10.00"), "req_1");
        CreateStripeRefundRequest refund = new CreateStripeRefundRequest("refund_1", "pi_1", new BigDecimal("10.00"));

        assertThrows(ForbiddenOperationException.class, () -> controller.createIntent(intent, null));
        assertThrows(ForbiddenOperationException.class, () -> controller.requestRefund(refund, null));
        assertThrows(ForbiddenOperationException.class, () -> controller.getRefund(1L, null));

        verifyNoInteractions(stripePaymentService, refundCoordinator);
    }

    @Test
    void rejectsTransientPrincipalBeforeCallingFinancialServices() {
        PaymentController controller = controller();
        User transientUser = new User("user@example.com", "Użytkownik");
        CreatePaymentIntentRequest intent = new CreatePaymentIntentRequest(new BigDecimal("10.00"), "req_1");
        CreateStripeRefundRequest refund = new CreateStripeRefundRequest("refund_1", "pi_1", new BigDecimal("10.00"));

        assertThrows(ForbiddenOperationException.class, () -> controller.createIntent(intent, transientUser));
        assertThrows(ForbiddenOperationException.class, () -> controller.requestRefund(refund, transientUser));
        assertThrows(ForbiddenOperationException.class, () -> controller.getRefund(1L, transientUser));

        verifyNoInteractions(stripePaymentService, refundCoordinator);
    }

    private PaymentController controller() {
        return new PaymentController(
                stripePaymentService,
                platformFeePolicy,
                platformFeeQuoteService,
                refundCoordinator
        );
    }
}
