package com.doFast.dofastapp.payment.refund.service;

import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.payment.refund.entity.StripeRefundRequest;
import com.doFast.dofastapp.payment.refund.entity.StripeRefundStatus;
import com.doFast.dofastapp.payment.refund.repository.StripeRefundEventRepository;
import com.doFast.dofastapp.payment.refund.repository.StripeRefundRequestRepository;
import com.doFast.dofastapp.wallet.enums.WalletTransactionType;
import com.doFast.dofastapp.wallet.service.WalletService;
import com.stripe.model.Refund;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Service
@Transactional
public class StripeRefundSettlementService {

    public static final String CREATED = "refund.created";
    public static final String UPDATED = "refund.updated";
    public static final String FAILED = "refund.failed";

    private final StripeRefundRequestRepository requestRepository;
    private final StripeRefundEventRepository eventRepository;
    private final WalletService walletService;

    public StripeRefundSettlementService(
            StripeRefundRequestRepository requestRepository,
            StripeRefundEventRepository eventRepository,
            WalletService walletService
    ) {
        this.requestRepository = requestRepository;
        this.eventRepository = eventRepository;
        this.walletService = walletService;
    }

    public StripeRefundSettlementResult process(
            Refund refund,
            String eventId,
            String eventType,
            Long eventCreatedAt
    ) {
        if (refund == null || refund.getId() == null || refund.getId().isBlank()) {
            throw new ConflictException("Stripe refund nie zawiera identyfikatora");
        }
        if (eventId == null || eventId.isBlank()) {
            throw new ConflictException("Stripe refund event nie zawiera identyfikatora");
        }

        StripeRefundRequest request = resolveManagedRequest(refund);
        if (request == null) {
            return StripeRefundSettlementResult.IGNORED;
        }
        request = requestRepository.findByIdForUpdate(request.getId())
                .orElseThrow(() -> new ConflictException("Lokalny refund request nie istnieje"));
        validateRefundMatchesRequest(refund, request);

        LocalDateTime now = LocalDateTime.now();
        int claimed = eventRepository.claim(
                eventId,
                request.getId(),
                refund.getId(),
                eventType,
                eventCreatedAt,
                now
        );
        if (claimed == 0) {
            return StripeRefundSettlementResult.DUPLICATE;
        }

        request.applyProviderEvent(
                refund.getId(),
                refund.getStatus(),
                refund.getFailureReason(),
                eventCreatedAt,
                now
        );
        restoreWalletIfProviderRejected(request, now);
        return StripeRefundSettlementResult.APPLIED;
    }

    private StripeRefundRequest resolveManagedRequest(Refund refund) {
        StripeRefundRequest byRefund = requestRepository.findByStripeRefundId(refund.getId()).orElse(null);
        if (byRefund != null) {
            return byRefund;
        }
        Map<String, String> metadata = refund.getMetadata();
        String requestIdValue = metadata != null ? metadata.get("dofastRefundId") : null;
        if (requestIdValue == null || requestIdValue.isBlank()) {
            return null;
        }
        try {
            return requestRepository.findById(Long.parseLong(requestIdValue)).orElse(null);
        } catch (NumberFormatException ex) {
            throw new ConflictException("Stripe refund ma błędny identyfikator doFast");
        }
    }

    private void validateRefundMatchesRequest(Refund refund, StripeRefundRequest request) {
        if (refund.getPaymentIntent() == null
                || !request.getStripePaymentIntentId().equals(refund.getPaymentIntent())) {
            throw new ConflictException("Stripe refund wskazuje inną płatność niż lokalny request");
        }
        if (refund.getAmount() == null
                || BigDecimal.valueOf(refund.getAmount(), 2).compareTo(request.getAmount()) != 0) {
            throw new ConflictException("Stripe refund ma inną kwotę niż lokalny request");
        }
        if (refund.getCurrency() == null || !request.getCurrency().equalsIgnoreCase(refund.getCurrency())) {
            throw new ConflictException("Stripe refund ma inną walutę niż lokalny request");
        }
        Map<String, String> metadata = refund.getMetadata();
        if (metadata != null) {
            String userId = metadata.get("userId");
            if (userId != null && !request.getUserId().toString().equals(userId)) {
                throw new ConflictException("Stripe refund ma innego użytkownika niż lokalny request");
            }
        }
    }

    private void restoreWalletIfProviderRejected(StripeRefundRequest request, LocalDateTime now) {
        if (request.getStatus() != StripeRefundStatus.FAILED
                && request.getStatus() != StripeRefundStatus.CANCELED) {
            return;
        }
        if (request.isWalletRestored()) {
            return;
        }
        boolean restored = walletService.creditRestoringOperation(
                request.getUserId(),
                request.getAmount(),
                WalletTransactionType.STRIPE_REFUND_RESTORE,
                null,
                restoreOperationKey(request.getId()),
                reserveOperationKey(request.getId())
        );
        if (!restored) {
            throw new ConflictException("Wykryto niespójny stan zwrotu rezerwacji zwrotu Stripe");
        }
        request.markWalletRestored(now);
    }

    private String reserveOperationKey(Long requestId) {
        return "stripe:refund:" + requestId + ":reserve";
    }

    private String restoreOperationKey(Long requestId) {
        return "stripe:refund:" + requestId + ":restore";
    }
}
