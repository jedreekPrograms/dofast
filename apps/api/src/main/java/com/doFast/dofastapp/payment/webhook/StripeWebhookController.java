package com.doFast.dofastapp.payment.webhook;

import com.doFast.dofastapp.common.exception.BusinessException;
import com.doFast.dofastapp.payment.entity.PaymentTransaction;
import com.doFast.dofastapp.payment.repository.PaymentTransactionRepository;
import com.doFast.dofastapp.wallet.enums.WalletTransactionType;
import com.doFast.dofastapp.wallet.service.WalletService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/webhooks/stripe")
public class StripeWebhookController {

    @Value("${stripe.webhook.secret}")
    private String endpointSecret;

    private final WalletService walletService;
    private final PaymentTransactionRepository paymentTransactionRepository;

    public StripeWebhookController(WalletService walletService, PaymentTransactionRepository paymentTransactionRepository) {
        this.walletService = walletService;
        this.paymentTransactionRepository = paymentTransactionRepository;
    }

    @PostMapping
    public ResponseEntity<String> handle(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        try {
            Event event = Webhook.constructEvent(payload, sigHeader, endpointSecret);

            if ("payment_intent.succeeded".equals(event.getType())) {

                ObjectMapper mapper = new ObjectMapper();

                JsonNode root = mapper.readTree(payload);
                JsonNode intentNode = root
                        .path("data")
                        .path("object");

                if (intentNode.isMissingNode()) {
                    System.out.println("❌ Brak data.object w webhooku");
                    return ResponseEntity.ok("ignored");
                }

                String paymentIntentId = intentNode.path("id").asText(null);


                String userIdStr = intentNode
                        .path("metadata")
                        .path("userId")
                        .asText(null);

                Long amountInCents = intentNode.path("amount").asLong();

                if (paymentIntentId == null || userIdStr == null) {
                    System.out.println("❌ Brak id lub userId");
                    return ResponseEntity.ok("ignored");
                }

                if (paymentTransactionRepository
                        .existsByStripePaymentIntentId(paymentIntentId)) {
                    System.out.println("⚠️ Duplikat webhooka");
                    return ResponseEntity.ok("already processed");
                }

                Long userId = Long.valueOf(userIdStr);
                BigDecimal amount = BigDecimal
                        .valueOf(amountInCents)
                        .divide(BigDecimal.valueOf(100));

                System.out.println("➡ PI = " + paymentIntentId);
                System.out.println("➡ userId = " + userId);
                System.out.println("➡ amount = " + amount);

                walletService.addMoney(userId, amount, WalletTransactionType.TOP_UP, null);

                paymentTransactionRepository.save(
                        new PaymentTransaction(paymentIntentId, userId, amount)
                );
            }

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.ok("error logged");
        }

        return ResponseEntity.ok("ok");
    }
}
