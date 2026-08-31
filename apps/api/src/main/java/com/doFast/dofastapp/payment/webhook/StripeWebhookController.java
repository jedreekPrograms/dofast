package com.doFast.dofastapp.payment.webhook;

import com.doFast.dofastapp.job.publication.JobPublicationPaymentIntentService;
import com.doFast.dofastapp.job.publication.JobPublicationStripeSettlementService;
import com.doFast.dofastapp.payment.refund.service.StripeRefundSettlementResult;
import com.doFast.dofastapp.payment.refund.service.StripeRefundSettlementService;
import com.doFast.dofastapp.payment.risk.service.StripePaymentDisputeService;
import com.doFast.dofastapp.payment.service.StripePaymentService;
import com.doFast.dofastapp.payout.provider.PayoutProviderSettlementResult;
import com.doFast.dofastapp.payout.service.StripeConnectPayoutSettlementService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Dispute;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Payout;
import com.stripe.model.Refund;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhooks/stripe")
public class StripeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);

    private final String endpointSecret;
    private final StripePaymentService stripePaymentService;
    private final JobPublicationStripeSettlementService publicationSettlementService;
    private final StripeConnectPayoutSettlementService payoutSettlementService;
    private final StripePaymentDisputeService paymentDisputeService;
    private final StripeRefundSettlementService refundSettlementService;

    public StripeWebhookController(
            @Value("${stripe.webhook.secret}") String endpointSecret,
            StripePaymentService stripePaymentService,
            JobPublicationStripeSettlementService publicationSettlementService,
            StripeConnectPayoutSettlementService payoutSettlementService,
            StripePaymentDisputeService paymentDisputeService,
            StripeRefundSettlementService refundSettlementService
    ) {
        this.endpointSecret = endpointSecret;
        this.stripePaymentService = stripePaymentService;
        this.publicationSettlementService = publicationSettlementService;
        this.payoutSettlementService = payoutSettlementService;
        this.paymentDisputeService = paymentDisputeService;
        this.refundSettlementService = refundSettlementService;
    }

    @PostMapping
    public ResponseEntity<String> handle(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String signatureHeader
    ) {
        final Event event;
        try {
            event = Webhook.constructEvent(payload, signatureHeader, endpointSecret);
        } catch (SignatureVerificationException ex) {
            log.warn("Rejected Stripe webhook with invalid signature");
            return ResponseEntity.badRequest().body("invalid signature");
        }

        if ("payment_intent.succeeded".equals(event.getType())) {
            return handleSuccessfulPayment(event);
        }
        if (isRefundEvent(event.getType())) {
            return handleRefundEvent(event);
        }
        if (isPaymentDisputeEvent(event.getType())) {
            return handlePaymentDisputeEvent(event);
        }
        if ("payout.paid".equals(event.getType())
                || "payout.failed".equals(event.getType())
                || "payout.updated".equals(event.getType())) {
            return handlePayoutEvent(event);
        }
        return ResponseEntity.ok("ignored");
    }

    private ResponseEntity<String> handleSuccessfulPayment(Event event) {
        StripeObject stripeObject = event.getDataObjectDeserializer().getObject().orElse(null);
        if (!(stripeObject instanceof PaymentIntent paymentIntent)) {
            log.error("Unable to deserialize Stripe event {} of type {}", event.getId(), event.getType());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("unable to deserialize event");
        }

        try {
            boolean processed = isJobPublication(paymentIntent)
                    ? publicationSettlementService.processSuccessfulPayment(paymentIntent, event.getId())
                    : stripePaymentService.processSuccessfulPayment(paymentIntent, event.getId());
            if (!processed) {
                log.info("Stripe event {} / PaymentIntent {} was already processed", event.getId(), paymentIntent.getId());
                return ResponseEntity.ok("already processed");
            }
            log.info("Processed Stripe event {} / PaymentIntent {}", event.getId(), paymentIntent.getId());
            return ResponseEntity.ok("ok");
        } catch (RuntimeException ex) {
            log.error("Failed to process Stripe event {} / PaymentIntent {}", event.getId(), paymentIntent.getId(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("processing failed");
        }
    }

    private ResponseEntity<String> handleRefundEvent(Event event) {
        StripeObject stripeObject = event.getDataObjectDeserializer().getObject().orElse(null);
        if (!(stripeObject instanceof Refund refund)) {
            log.error("Unable to deserialize Stripe refund event {} of type {}", event.getId(), event.getType());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("unable to deserialize event");
        }
        try {
            StripeRefundSettlementResult result = refundSettlementService.process(
                    refund,
                    event.getId(),
                    event.getType(),
                    event.getCreated()
            );
            if (result == StripeRefundSettlementResult.IGNORED) {
                return ResponseEntity.ok("ignored");
            }
            if (result == StripeRefundSettlementResult.DUPLICATE) {
                return ResponseEntity.ok("already processed");
            }
            log.info("Processed Stripe refund event {} / refund {}", event.getId(), refund.getId());
            return ResponseEntity.ok("ok");
        } catch (RuntimeException ex) {
            log.error("Failed to process Stripe refund event {} / refund {}", event.getId(), refund.getId(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("processing failed");
        }
    }

    private ResponseEntity<String> handlePaymentDisputeEvent(Event event) {
        StripeObject stripeObject = event.getDataObjectDeserializer().getObject().orElse(null);
        if (!(stripeObject instanceof Dispute dispute)) {
            log.error("Unable to deserialize Stripe dispute event {} of type {}", event.getId(), event.getType());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("unable to deserialize event");
        }

        try {
            boolean processed = paymentDisputeService.process(
                    dispute,
                    event.getId(),
                    event.getType(),
                    event.getCreated()
            );
            if (!processed) {
                log.info("Stripe dispute event {} / dispute {} was already processed", event.getId(), dispute.getId());
                return ResponseEntity.ok("already processed");
            }
            log.info("Processed Stripe dispute event {} / dispute {}", event.getId(), dispute.getId());
            return ResponseEntity.ok("ok");
        } catch (RuntimeException ex) {
            log.error("Failed to process Stripe dispute event {} / dispute {}", event.getId(), dispute.getId(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("processing failed");
        }
    }

    private ResponseEntity<String> handlePayoutEvent(Event event) {
        StripeObject stripeObject = event.getDataObjectDeserializer().getObject().orElse(null);
        if (!(stripeObject instanceof Payout payout)) {
            log.error("Unable to deserialize Stripe event {} of type {}", event.getId(), event.getType());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("unable to deserialize event");
        }

        try {
            PayoutProviderSettlementResult result = payoutSettlementService.process(
                    payout,
                    event.getId(),
                    event.getAccount(),
                    event.getCreated()
            );
            if (result == null) {
                return ResponseEntity.ok("ignored");
            }
            log.info("Processed Stripe Connect payout event {} / payout {} as {}", event.getId(), payout.getId(), result);
            return ResponseEntity.ok(result == PayoutProviderSettlementResult.APPLIED ? "ok" : "already processed");
        } catch (RuntimeException ex) {
            log.error("Failed to process Stripe Connect payout event {} / payout {}", event.getId(), payout.getId(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("processing failed");
        }
    }

    private boolean isRefundEvent(String eventType) {
        return StripeRefundSettlementService.CREATED.equals(eventType)
                || StripeRefundSettlementService.UPDATED.equals(eventType)
                || StripeRefundSettlementService.FAILED.equals(eventType);
    }

    private boolean isPaymentDisputeEvent(String eventType) {
        return StripePaymentDisputeService.CREATED.equals(eventType)
                || StripePaymentDisputeService.UPDATED.equals(eventType)
                || StripePaymentDisputeService.CLOSED.equals(eventType)
                || StripePaymentDisputeService.FUNDS_WITHDRAWN.equals(eventType)
                || StripePaymentDisputeService.FUNDS_REINSTATED.equals(eventType);
    }

    private boolean isJobPublication(PaymentIntent paymentIntent) {
        return paymentIntent.getMetadata() != null
                && JobPublicationPaymentIntentService.PURPOSE.equals(paymentIntent.getMetadata().get("purpose"));
    }
}
