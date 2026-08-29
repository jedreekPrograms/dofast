package com.doFast.dofastapp.payment.refund.service;

import com.doFast.dofastapp.common.exception.BusinessException;
import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
import com.doFast.dofastapp.payment.entity.PaymentTransaction;
import com.doFast.dofastapp.payment.refund.dto.CreateStripeRefundRequest;
import com.doFast.dofastapp.payment.refund.dto.StripeRefundResponse;
import com.doFast.dofastapp.payment.refund.entity.StripeRefundRequest;
import com.doFast.dofastapp.payment.refund.entity.StripeRefundStatus;
import com.doFast.dofastapp.payment.refund.repository.StripeRefundRequestRepository;
import com.doFast.dofastapp.payment.repository.PaymentTransactionRepository;
import com.doFast.dofastapp.payment.risk.repository.StripePaymentDisputeRepository;
import com.doFast.dofastapp.wallet.enums.WalletTransactionType;
import com.doFast.dofastapp.wallet.service.WalletService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class StripeRefundRequestService {

    private static final String CURRENCY = "PLN";
    private static final int MAX_DISPATCH_ATTEMPTS = 8;

    private final StripeRefundRequestRepository refundRepository;
    private final PaymentTransactionRepository paymentRepository;
    private final StripePaymentDisputeRepository disputeRepository;
    private final WalletService walletService;

    public StripeRefundRequestService(
            StripeRefundRequestRepository refundRepository,
            PaymentTransactionRepository paymentRepository,
            StripePaymentDisputeRepository disputeRepository,
            WalletService walletService
    ) {
        this.refundRepository = refundRepository;
        this.paymentRepository = paymentRepository;
        this.disputeRepository = disputeRepository;
        this.walletService = walletService;
    }

    @Transactional
    public StripeRefundResponse create(Long userId, CreateStripeRefundRequest input) {
        if (userId == null) {
            throw new ForbiddenOperationException("Zaloguj się, aby zlecić zwrot");
        }
        String requestKey = normalizeRequestKey(input.requestId());
        String paymentIntentId = input.paymentIntentId().trim();

        StripeRefundRequest existing = refundRepository.findByUserIdAndRequestKey(userId, requestKey).orElse(null);
        if (existing != null) {
            return validateIdempotentRequest(existing, paymentIntentId, input.amount());
        }

        PaymentTransaction payment = paymentRepository.findByStripePaymentIntentIdForUpdate(paymentIntentId)
                .orElseThrow(() -> new ResourceNotFoundException("Płatność Stripe nie istnieje"));
        if (!userId.equals(payment.getUserId())) {
            throw new ForbiddenOperationException("Ta płatność Stripe należy do innego użytkownika");
        }
        if (!CURRENCY.equalsIgnoreCase(payment.getCurrency())) {
            throw new BusinessException("Zwroty do oryginalnej metody są obsługiwane tylko dla PLN");
        }
        if (!"TOP_UP".equals(payment.getSettlementPurpose())
                && !"JOB_PUBLICATION".equals(payment.getSettlementPurpose())) {
            throw new ConflictException("Ten typ płatności Stripe nie obsługuje zwrotu do oryginalnej metody");
        }
        if (disputeRepository.findByStripePaymentIntentId(paymentIntentId).isPresent()) {
            throw new ConflictException("Płatność ma lub miała dispute Stripe i nie może zostać dodatkowo zwrócona");
        }

        existing = refundRepository.findByUserIdAndRequestKey(userId, requestKey).orElse(null);
        if (existing != null) {
            return validateIdempotentRequest(existing, paymentIntentId, input.amount());
        }

        BigDecimal committed = money(refundRepository.sumCommittedAmount(paymentIntentId));
        BigDecimal remaining = payment.getAmount().subtract(committed).setScale(2, RoundingMode.UNNECESSARY);
        if (remaining.signum() <= 0) {
            throw new ConflictException("Ta płatność Stripe została już w całości zwrócona lub zarezerwowana do zwrotu");
        }

        BigDecimal amount = input.amount() == null ? remaining : money(input.amount());
        if (amount.signum() <= 0 || amount.compareTo(remaining) > 0) {
            throw new BusinessException("Kwota zwrotu przekracza pozostałą kwotę możliwą do zwrotu");
        }

        LocalDateTime now = LocalDateTime.now();
        StripeRefundRequest request = refundRepository.saveAndFlush(
                StripeRefundRequest.create(userId, paymentIntentId, requestKey, amount, CURRENCY, now)
        );
        walletService.debitFromStripePayment(
                userId,
                amount,
                WalletTransactionType.STRIPE_REFUND_RESERVE,
                null,
                reserveOperationKey(request.getId()),
                paymentIntentId
        );
        return toResponse(request);
    }

    public StripeRefundResponse get(Long refundRequestId, Long userId) {
        StripeRefundRequest request = refundRepository.findById(refundRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Zwrot nie istnieje"));
        if (!request.getUserId().equals(userId)) {
            throw new ForbiddenOperationException("Ten zwrot należy do innego użytkownika");
        }
        return toResponse(request);
    }

    @Transactional
    public StripeRefundDispatchCommand claimForDispatch(Long requestId) {
        StripeRefundRequest request = refundRepository.findByIdForUpdate(requestId).orElse(null);
        LocalDateTime now = LocalDateTime.now();
        if (request == null
                || request.getStatus() != StripeRefundStatus.REQUESTED
                || request.getNextAttemptAt() == null
                || request.getNextAttemptAt().isAfter(now)) {
            return null;
        }

        if (disputeRepository.findByStripePaymentIntentId(request.getStripePaymentIntentId()).isPresent()) {
            if (request.getAttemptCount() == 0 && request.getStripeRefundId() == null) {
                request.cancelBeforeFirstDispatch("payment_disputed", now);
                restoreWalletIfProviderRejected(request);
            } else {
                request.markReviewRequired("payment_disputed_during_refund", now);
            }
            return null;
        }

        request.startDispatch(now);
        return new StripeRefundDispatchCommand(
                request.getId(),
                request.getUserId(),
                request.getStripePaymentIntentId(),
                request.getAmount(),
                request.getCurrency(),
                request.getAttemptCount()
        );
    }

    @Transactional
    public void recordProviderResult(Long requestId, StripeRefundProviderResult result) {
        StripeRefundRequest request = refundRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Zwrot nie istnieje"));
        request.recordSubmission(result.refundId(), result.status(), LocalDateTime.now());
        restoreWalletIfProviderRejected(request);
    }

    @Transactional
    public void recordDispatchFailure(Long requestId) {
        StripeRefundRequest request = refundRepository.findByIdForUpdate(requestId).orElse(null);
        if (request == null || request.getStatus() != StripeRefundStatus.DISPATCHING) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        if (request.getAttemptCount() >= MAX_DISPATCH_ATTEMPTS) {
            request.markReviewRequired("provider_request_ambiguous", now);
            return;
        }
        long delaySeconds = Math.min(300L, 1L << Math.min(request.getAttemptCount(), 8));
        request.reschedule("provider_request_ambiguous", now.plusSeconds(delaySeconds), now);
    }

    @Transactional
    public int requeueStaleDispatches() {
        LocalDateTime now = LocalDateTime.now();
        return refundRepository.requeueStaleDispatches(now.minusMinutes(2), now);
    }

    public List<Long> findDispatchableIds(int limit) {
        return refundRepository.findDispatchableIds(LocalDateTime.now(), limit);
    }

    private void restoreWalletIfProviderRejected(StripeRefundRequest request) {
        if (request.getStatus() != StripeRefundStatus.FAILED
                && request.getStatus() != StripeRefundStatus.CANCELED) {
            return;
        }
        if (request.isWalletRestored()) {
            return;
        }
        walletService.creditRestoringOperation(
                request.getUserId(),
                request.getAmount(),
                WalletTransactionType.STRIPE_REFUND_RESTORE,
                null,
                restoreOperationKey(request.getId()),
                reserveOperationKey(request.getId())
        );
        request.markWalletRestored(LocalDateTime.now());
    }

    private StripeRefundResponse validateIdempotentRequest(
            StripeRefundRequest existing,
            String paymentIntentId,
            BigDecimal requestedAmount
    ) {
        boolean samePayment = existing.getStripePaymentIntentId().equals(paymentIntentId);
        boolean sameAmount = requestedAmount == null || existing.getAmount().compareTo(money(requestedAmount)) == 0;
        if (!samePayment || !sameAmount) {
            throw new ConflictException("Identyfikator żądania zwrotu został już użyty dla innych danych");
        }
        return toResponse(existing);
    }

    StripeRefundResponse toResponse(StripeRefundRequest request) {
        return new StripeRefundResponse(
                request.getId(),
                request.getStripePaymentIntentId(),
                request.getAmount(),
                request.getCurrency(),
                request.getStatus(),
                request.getStripeRefundId(),
                request.getFailureReason(),
                request.getAttemptCount(),
                request.getCreatedAt(),
                request.getUpdatedAt(),
                request.getResolvedAt()
        );
    }

    private BigDecimal money(BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new BusinessException("Kwota może mieć maksymalnie dwa miejsca po przecinku");
        }
    }

    private String normalizeRequestKey(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            throw new BusinessException("Identyfikator żądania zwrotu jest wymagany");
        }
        String normalized = requestId.trim();
        if (normalized.length() > 96) {
            throw new BusinessException("Identyfikator żądania zwrotu jest za długi");
        }
        return normalized;
    }

    private String reserveOperationKey(Long requestId) {
        return "stripe:refund:" + requestId + ":reserve";
    }

    private String restoreOperationKey(Long requestId) {
        return "stripe:refund:" + requestId + ":restore";
    }
}
