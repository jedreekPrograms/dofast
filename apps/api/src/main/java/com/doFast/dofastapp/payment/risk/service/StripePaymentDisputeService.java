package com.doFast.dofastapp.payment.risk.service;

import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.payment.entity.PaymentTransaction;
import com.doFast.dofastapp.payment.repository.PaymentTransactionRepository;
import com.doFast.dofastapp.payment.risk.entity.StripePaymentDispute;
import com.doFast.dofastapp.payment.risk.repository.StripePaymentDisputeEventRepository;
import com.doFast.dofastapp.payment.risk.repository.StripePaymentDisputeRepository;
import com.doFast.dofastapp.wallet.enums.WalletTransactionType;
import com.doFast.dofastapp.wallet.service.WalletService;
import com.stripe.model.Dispute;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class StripePaymentDisputeService {

    public static final String CREATED = "charge.dispute.created";
    public static final String UPDATED = "charge.dispute.updated";
    public static final String CLOSED = "charge.dispute.closed";
    public static final String FUNDS_WITHDRAWN = "charge.dispute.funds_withdrawn";
    public static final String FUNDS_REINSTATED = "charge.dispute.funds_reinstated";

    private static final String CURRENCY = "PLN";

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final StripePaymentDisputeRepository disputeRepository;
    private final StripePaymentDisputeEventRepository eventRepository;
    private final StripeChargebackRecoveryService recoveryService;
    private final WalletService walletService;

    public StripePaymentDisputeService(
            PaymentTransactionRepository paymentTransactionRepository,
            StripePaymentDisputeRepository disputeRepository,
            StripePaymentDisputeEventRepository eventRepository,
            StripeChargebackRecoveryService recoveryService,
            WalletService walletService
    ) {
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.disputeRepository = disputeRepository;
        this.eventRepository = eventRepository;
        this.recoveryService = recoveryService;
        this.walletService = walletService;
    }

    @Transactional
    public boolean process(Dispute stripeDispute, String eventId, String eventType) {
        if (stripeDispute == null) {
            throw new IllegalStateException("Stripe event does not contain a dispute");
        }
        String disputeId = requireValue(stripeDispute.getId(), "Stripe dispute is missing id", 255);
        String paymentIntentId = requireValue(
                stripeDispute.getPaymentIntent(),
                "Stripe dispute is missing PaymentIntent identity",
                255
        );
        String normalizedEventId = requireValue(eventId, "Stripe dispute event is missing id", 255);
        String normalizedEventType = requireSupportedEventType(eventType);
        String status = requireValue(stripeDispute.getStatus(), "Stripe dispute is missing status", 32);
        String currency = requireValue(stripeDispute.getCurrency(), "Stripe dispute is missing currency", 3)
                .toUpperCase(Locale.ROOT);
        if (!CURRENCY.equals(currency)) {
            throw new IllegalStateException("Stripe dispute uses unsupported currency");
        }
        Long amountInCents = stripeDispute.getAmount();
        if (amountInCents == null || amountInCents <= 0) {
            throw new IllegalStateException("Stripe dispute contains invalid amount");
        }
        BigDecimal amount = BigDecimal.valueOf(amountInCents, 2);

        PaymentTransaction payment = paymentTransactionRepository.findByStripePaymentIntentId(paymentIntentId)
                .orElseThrow(() -> new IllegalStateException("Stripe dispute references an unsettled PaymentIntent"));
        if (!CURRENCY.equalsIgnoreCase(payment.getCurrency())) {
            throw new IllegalStateException("Original Stripe settlement uses unsupported currency");
        }
        if (amount.compareTo(payment.getAmount()) > 0) {
            throw new IllegalStateException("Stripe dispute amount exceeds the settled PaymentIntent amount");
        }

        LocalDateTime now = LocalDateTime.now();
        StripePaymentDispute exposure = resolveExposure(
                stripeDispute,
                disputeId,
                paymentIntentId,
                payment,
                amount,
                currency,
                status,
                now
        );

        int claimed = eventRepository.claim(normalizedEventId, disputeId, normalizedEventType, now);
        if (claimed == 0) {
            if (eventRepository.countMatching(normalizedEventId, disputeId, normalizedEventType) == 1) {
                return false;
            }
            throw new ConflictException("Stripe event id was already assigned to another dispute event");
        }

        switch (normalizedEventType) {
            case FUNDS_WITHDRAWN -> {
                exposure.markFundsWithdrawn(now);
                disputeRepository.saveAndFlush(exposure);
                recoveryService.recoverAvailableBalance(exposure.getId());
            }
            case FUNDS_REINSTATED -> reinstateRecoveredWalletFunds(exposure, now);
            case CREATED, UPDATED, CLOSED -> disputeRepository.save(exposure);
            default -> throw new IllegalStateException("Unsupported Stripe dispute event");
        }
        return true;
    }

    private StripePaymentDispute resolveExposure(
            Dispute stripeDispute,
            String disputeId,
            String paymentIntentId,
            PaymentTransaction payment,
            BigDecimal amount,
            String currency,
            String status,
            LocalDateTime now
    ) {
        StripePaymentDispute exposure = disputeRepository.findByStripeDisputeId(disputeId).orElse(null);
        if (exposure == null) {
            StripePaymentDispute byIntent = disputeRepository.findByStripePaymentIntentId(paymentIntentId).orElse(null);
            if (byIntent != null) {
                throw new ConflictException("PaymentIntent is already associated with another Stripe dispute");
            }
            exposure = new StripePaymentDispute();
            exposure.initialize(
                    disputeId,
                    paymentIntentId,
                    stripeDispute.getCharge(),
                    payment.getUserId(),
                    amount,
                    currency,
                    nullableLimited(stripeDispute.getReason(), 64, "Stripe dispute reason is too long"),
                    status,
                    now
            );
            return disputeRepository.saveAndFlush(exposure);
        }

        boolean samePayment = exposure.getStripePaymentIntentId().equals(paymentIntentId);
        boolean sameUser = exposure.getUserId().equals(payment.getUserId());
        boolean sameAmount = exposure.getDisputedAmount().compareTo(amount) == 0;
        boolean sameCurrency = exposure.getCurrency().equalsIgnoreCase(currency);
        if (!samePayment || !sameUser || !sameAmount || !sameCurrency) {
            throw new ConflictException("Stripe dispute identity changed across webhook events");
        }
        exposure.refresh(
                stripeDispute.getCharge(),
                nullableLimited(stripeDispute.getReason(), 64, "Stripe dispute reason is too long"),
                status,
                now
        );
        return exposure;
    }

    private void reinstateRecoveredWalletFunds(StripePaymentDispute exposure, LocalDateTime now) {
        BigDecimal returnAmount = exposure.amountToReturnToWallet();
        if (returnAmount.signum() > 0) {
            boolean credited = walletService.creditRestoringOperationPrefix(
                    exposure.getUserId(),
                    returnAmount,
                    WalletTransactionType.CHARGEBACK_REINSTATEMENT,
                    null,
                    "stripe:dispute:" + exposure.getStripeDisputeId() + ":reinstatement",
                    WalletTransactionType.CHARGEBACK_RECOVERY,
                    "stripe:dispute:" + exposure.getStripeDisputeId() + ":recovery:"
            );
            if (!credited) {
                throw new ConflictException("Chargeback reinstatement ledger exists without matching dispute state");
            }
        }
        exposure.markFundsReinstated(returnAmount, now);
        disputeRepository.save(exposure);
    }

    private String requireSupportedEventType(String eventType) {
        String value = requireValue(eventType, "Stripe dispute event is missing type", 64);
        return switch (value) {
            case CREATED, UPDATED, CLOSED, FUNDS_WITHDRAWN, FUNDS_REINSTATED -> value;
            default -> throw new IllegalStateException("Unsupported Stripe dispute event type");
        };
    }

    private String requireValue(String value, String message, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(message);
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalStateException(message);
        }
        return normalized;
    }

    private String nullableLimited(String value, int maxLength, String message) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalStateException(message);
        }
        return normalized;
    }
}
