package com.doFast.dofastapp.payment.service;

import com.doFast.dofastapp.payment.entity.PaymentTransaction;
import com.doFast.dofastapp.payment.repository.PaymentTransactionRepository;
import com.doFast.dofastapp.wallet.enums.WalletTransactionType;
import com.doFast.dofastapp.wallet.service.WalletService;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

@Service
public class StripePaymentService {

    private final WalletService walletService;
    private final PaymentTransactionRepository paymentTransactionRepository;

    public StripePaymentService(
            WalletService walletService,
            PaymentTransactionRepository paymentTransactionRepository
    ) {
        this.walletService = walletService;
        this.paymentTransactionRepository = paymentTransactionRepository;
    }

    public PaymentIntent createPaymentIntent(BigDecimal amount, Long userId) throws StripeException {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Kwota płatności musi być dodatnia");
        }

        long amountInCents = amount.movePointRight(2).longValueExact();

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amountInCents)
                .setCurrency("pln")
                .putMetadata("userId", userId.toString())
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                .setEnabled(true)
                                .setAllowRedirects(
                                        PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.NEVER
                                )
                                .build()
                )
                .build();

        return PaymentIntent.create(params);
    }

    @Transactional
    public boolean processSuccessfulPayment(PaymentIntent paymentIntent) {
        String paymentIntentId = paymentIntent.getId();

        if (paymentIntentId == null || paymentIntentId.isBlank()) {
            throw new IllegalStateException("Stripe PaymentIntent does not contain an id");
        }

        if (paymentTransactionRepository.existsByStripePaymentIntentId(paymentIntentId)) {
            return false;
        }

        Map<String, String> metadata = paymentIntent.getMetadata();
        String userIdValue = metadata != null ? metadata.get("userId") : null;
        if (userIdValue == null || userIdValue.isBlank()) {
            throw new IllegalStateException("Stripe PaymentIntent is missing userId metadata");
        }

        Long amountInCents = paymentIntent.getAmount();
        if (amountInCents == null || amountInCents <= 0) {
            throw new IllegalStateException("Stripe PaymentIntent contains an invalid amount");
        }

        long userId;
        try {
            userId = Long.parseLong(userIdValue);
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("Stripe PaymentIntent contains invalid userId metadata", ex);
        }

        BigDecimal amount = BigDecimal.valueOf(amountInCents, 2);
        walletService.addMoney(userId, amount, WalletTransactionType.TOP_UP, null);
        paymentTransactionRepository.saveAndFlush(
                new PaymentTransaction(paymentIntentId, userId, amount)
        );

        return true;
    }
}
