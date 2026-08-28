package com.doFast.dofastapp.payment.webhook;

import com.doFast.dofastapp.job.publication.JobPublicationPaymentIntentService;
import com.doFast.dofastapp.job.publication.JobPublicationStripeSettlementService;
import com.doFast.dofastapp.payment.service.StripePaymentService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
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

    public StripeWebhookController(
            @Value("${stripe.webhook.secret}") String endpointSecret,
            StripePaymentService stripePaymentService,
            JobPublicationStripeSettlementService publicationSettlementService
    ) {
        this.endpointSecret = endpointSecret;
        this.stripePaymentService = stripePaymentService;
        this.publicationSettlementService = publicationSettlementService;
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

        if (!"payment_intent.succeeded".equals(event.getType())) {
            return ResponseEntity.ok("ignored");
        }

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
            log.error(
                    "Failed to process Stripe event {} / PaymentIntent {}",
                    event.getId(),
                    paymentIntent.getId(),
                    ex
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("processing failed");
        }
    }

    private boolean isJobPublication(PaymentIntent paymentIntent) {
        return paymentIntent.getMetadata() != null
                && JobPublicationPaymentIntentService.PURPOSE.equals(paymentIntent.getMetadata().get("purpose"));
    }
}
