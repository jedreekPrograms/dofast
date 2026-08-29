package com.doFast.dofastapp.payment.service;

import com.doFast.dofastapp.common.exception.BusinessException;
import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.payment.dto.CreatePaymentIntentResponse;
import com.doFast.dofastapp.payment.entity.PaymentTransaction;
import com.doFast.dofastapp.payment.exception.PaymentProviderException;
import com.doFast.dofastapp.payment.repository.PaymentTransactionRepository;
import com.doFast.dofastapp.wallet.enums.WalletTransactionType;
import com.doFast.dofastapp.wallet.service.WalletService;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;

@Service
public class StripePaymentService {

    public static final String PURPOSE = "TOP_UP";

    private static final String CURRENCY = "PLN";
    private static final BigDecimal MIN_TOP_UP_AMOUNT = new BigDecimal("1.00");
    private static final BigDecimal MAX_TOP_UP_AMOUNT = new BigDecimal("10000.00");

    private final WalletService walletService;
    private final PaymentTransactionRepository paymentTransactionRepository;

    public StripePaymentService(
            WalletService walletService,
            PaymentTransactionRepository paymentTransactionRepository
    ) {
        this.walletService = walletService;
        this.paymentTransactionRepository = paymentTransactionRepository;
    }

    public CreatePaymentIntentResponse createPaymentIntent(BigDecimal amount, Long userId, String requestId) {
        BigDecimal normalizedAmount = normalizeAmount(amount);
        String normalizedRequestId = normalizeRequestId(requestId);
        long amountInCents = normalizedAmount.movePointRight(2).longValueExact();

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amountInCents)
                .setCurrency(CURRENCY.toLowerCase(Locale.ROOT))
                .putMetadata("userId", userId.toString())
                .putMetadata("purpose", PURPOSE)
                .putMetadata("topUpRequestId", normalizedRequestId)
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                .setEnabled(true)
                                .setAllowRedirects(
                                        PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.NEVER
                                )
                                .build()
                )
                .build();

        RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey("dofast:topup:" + userId + ":" + normalizedRequestId)
                .build();

        try {
            PaymentIntent intent = PaymentIntent.create(params, options);
            if (intent.getId() == null || intent.getId().isBlank()
                    || intent.getClientSecret() == null || intent.getClientSecret().isBlank()) {
                throw new PaymentProviderException("Stripe zwrócił niepełną odpowiedź dla PaymentIntent", null);
            }
            return new CreatePaymentIntentResponse(
                    intent.getId(),
                    intent.getClientSecret(),
                    normalizedAmount,
                    CURRENCY
            );
        } catch (StripeException ex) {
            throw new PaymentProviderException("Nie udało się utworzyć płatności Stripe", ex);
        }
    }

    @Transactional
    public boolean processSuccessfulPayment(PaymentIntent paymentIntent, String eventId) {
        if (paymentIntent == null) {
            throw new IllegalStateException("Stripe event does not contain a PaymentIntent");
        }

        String paymentIntentId = paymentIntent.getId();
        if (paymentIntentId == null || paymentIntentId.isBlank()) {
            throw new IllegalStateException("Stripe PaymentIntent does not contain an id");
        }
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalStateException("Stripe event does not contain an id");
        }
        if (!"succeeded".equals(paymentIntent.getStatus())) {
            throw new IllegalStateException("Stripe PaymentIntent is not in succeeded state");
        }

        String currency = paymentIntent.getCurrency();
        if (currency == null || !CURRENCY.equalsIgnoreCase(currency)) {
            throw new IllegalStateException("Stripe PaymentIntent uses unsupported currency");
        }

        Map<String, String> metadata = paymentIntent.getMetadata();
        validateTopUpPurpose(metadata);
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
        if (amount.compareTo(MIN_TOP_UP_AMOUNT) < 0 || amount.compareTo(MAX_TOP_UP_AMOUNT) > 0) {
            throw new IllegalStateException("Stripe PaymentIntent amount is outside the supported top-up range");
        }

        int claimed = paymentTransactionRepository.claimSuccessfulPayment(
                paymentIntentId,
                eventId,
                userId,
                amount,
                CURRENCY,
                LocalDateTime.now()
        );
        if (claimed == 0) {
            return validateExistingClaim(paymentIntentId, eventId, userId, amount);
        }

        boolean credited = walletService.credit(
                userId,
                amount,
                WalletTransactionType.TOP_UP,
                null,
                "stripe:intent:" + paymentIntentId
        );
        if (!credited) {
            throw new ConflictException("Wpłata Stripe istnieje w ledgerze, ale nie została poprawnie rozliczona");
        }

        return true;
    }

    private void validateTopUpPurpose(Map<String, String> metadata) {
        String purpose = metadata != null ? metadata.get("purpose") : null;
        String topUpRequestId = metadata != null ? metadata.get("topUpRequestId") : null;
        String jobPublicationId = metadata != null ? metadata.get("jobPublicationId") : null;

        if (PURPOSE.equals(purpose)) {
            if (topUpRequestId == null || topUpRequestId.isBlank()) {
                throw new IllegalStateException("Stripe top-up PaymentIntent is missing topUpRequestId metadata");
            }
            return;
        }

        boolean legacyTopUp = (purpose == null || purpose.isBlank())
                && jobPublicationId == null
                && topUpRequestId != null
                && !topUpRequestId.isBlank();
        if (!legacyTopUp) {
            throw new IllegalStateException("Stripe PaymentIntent is not a wallet top-up");
        }
    }

    private boolean validateExistingClaim(
            String paymentIntentId,
            String eventId,
            long userId,
            BigDecimal amount
    ) {
        PaymentTransaction byIntent = paymentTransactionRepository
                .findByStripePaymentIntentId(paymentIntentId)
                .orElse(null);

        if (byIntent != null) {
            boolean sameUser = byIntent.getUserId().equals(userId);
            boolean sameAmount = byIntent.getAmount().compareTo(amount) == 0;
            boolean sameCurrency = CURRENCY.equalsIgnoreCase(byIntent.getCurrency());
            if (!sameUser || !sameAmount || !sameCurrency) {
                throw new ConflictException("Ten PaymentIntent Stripe został już rozliczony z innymi danymi");
            }
            return false;
        }

        if (paymentTransactionRepository.findByStripeEventId(eventId).isPresent()) {
            throw new ConflictException("Ten event Stripe został już przypisany do innego PaymentIntent");
        }

        throw new ConflictException("Nie udało się jednoznacznie rozpoznać duplikatu płatności Stripe");
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null) {
            throw new BusinessException("Kwota płatności jest wymagana");
        }

        final BigDecimal normalizedAmount;
        try {
            normalizedAmount = amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new BusinessException("Kwota płatności może mieć maksymalnie dwa miejsca po przecinku");
        }

        if (normalizedAmount.compareTo(MIN_TOP_UP_AMOUNT) < 0
                || normalizedAmount.compareTo(MAX_TOP_UP_AMOUNT) > 0) {
            throw new BusinessException("Kwota doładowania musi mieścić się w przedziale 1,00–10 000,00 PLN");
        }
        return normalizedAmount;
    }

    private String normalizeRequestId(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            throw new BusinessException("Identyfikator żądania płatności jest wymagany");
        }
        return requestId.trim();
    }
}
