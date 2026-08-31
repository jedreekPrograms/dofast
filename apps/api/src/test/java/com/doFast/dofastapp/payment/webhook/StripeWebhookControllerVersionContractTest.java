package com.doFast.dofastapp.payment.webhook;

import com.doFast.dofastapp.job.publication.JobPublicationStripeSettlementService;
import com.doFast.dofastapp.payment.refund.service.StripeRefundSettlementService;
import com.doFast.dofastapp.payment.risk.service.StripePaymentDisputeService;
import com.doFast.dofastapp.payment.service.StripePaymentService;
import com.doFast.dofastapp.payout.service.StripeConnectPayoutSettlementService;
import com.stripe.net.Webhook;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class StripeWebhookControllerVersionContractTest {

    private static final String WEBHOOK_SECRET = "whsec_version_contract_test";

    @Test
    void signedFinancialWebhookWithMismatchedApiVersionFailsBeforeSettlement() throws Exception {
        StripePaymentService paymentService = mock(StripePaymentService.class);
        JobPublicationStripeSettlementService publicationService = mock(JobPublicationStripeSettlementService.class);
        StripeConnectPayoutSettlementService payoutService = mock(StripeConnectPayoutSettlementService.class);
        StripePaymentDisputeService disputeService = mock(StripePaymentDisputeService.class);
        StripeRefundSettlementService refundService = mock(StripeRefundSettlementService.class);
        StripeWebhookController controller = new StripeWebhookController(
                WEBHOOK_SECRET,
                paymentService,
                publicationService,
                payoutService,
                disputeService,
                refundService
        );
        String payload = eventPayload("payment_intent.succeeded", "2026-08-26.dahlia");
        String signature = Webhook.Signature.generateSignatureHeader(payload, WEBHOOK_SECRET);

        ResponseEntity<String> response = controller.handle(payload, signature);

        assertEquals(500, response.getStatusCode().value());
        assertEquals("unsupported stripe api version", response.getBody());
        verifyNoInteractions(paymentService, publicationService, payoutService, disputeService, refundService);
    }

    @Test
    void unhandledWebhookWithDifferentApiVersionRemainsIgnored() throws Exception {
        StripePaymentService paymentService = mock(StripePaymentService.class);
        JobPublicationStripeSettlementService publicationService = mock(JobPublicationStripeSettlementService.class);
        StripeConnectPayoutSettlementService payoutService = mock(StripeConnectPayoutSettlementService.class);
        StripePaymentDisputeService disputeService = mock(StripePaymentDisputeService.class);
        StripeRefundSettlementService refundService = mock(StripeRefundSettlementService.class);
        StripeWebhookController controller = new StripeWebhookController(
                WEBHOOK_SECRET,
                paymentService,
                publicationService,
                payoutService,
                disputeService,
                refundService
        );
        String payload = eventPayload("customer.created", "2026-08-26.dahlia");
        String signature = Webhook.Signature.generateSignatureHeader(payload, WEBHOOK_SECRET);

        ResponseEntity<String> response = controller.handle(payload, signature);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("ignored", response.getBody());
        verifyNoInteractions(paymentService, publicationService, payoutService, disputeService, refundService);
    }

    private String eventPayload(String eventType, String apiVersion) {
        return "{"
                + "\"id\":\"evt_version_contract\","
                + "\"object\":\"event\","
                + "\"api_version\":\"" + apiVersion + "\","
                + "\"created\":1700000000,"
                + "\"data\":{\"object\":{}},"
                + "\"livemode\":false,"
                + "\"pending_webhooks\":1,"
                + "\"type\":\"" + eventType + "\""
                + "}";
    }
}
