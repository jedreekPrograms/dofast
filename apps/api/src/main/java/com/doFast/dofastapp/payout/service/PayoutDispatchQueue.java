package com.doFast.dofastapp.payout.service;

import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.payout.config.PayoutProperties;
import com.doFast.dofastapp.payout.entity.PayoutEvent;
import com.doFast.dofastapp.payout.entity.PayoutRequest;
import com.doFast.dofastapp.payout.enums.PayoutEventSource;
import com.doFast.dofastapp.payout.enums.PayoutEventType;
import com.doFast.dofastapp.payout.enums.PayoutStatus;
import com.doFast.dofastapp.payout.provider.PayoutDispatchCommand;
import com.doFast.dofastapp.payout.provider.PayoutDispatchResult;
import com.doFast.dofastapp.payout.repository.PayoutEventRepository;
import com.doFast.dofastapp.payout.repository.PayoutRequestRepository;
import com.doFast.dofastapp.user.enums.UserStatus;
import com.doFast.dofastapp.user.repository.UserRepository;
import com.doFast.dofastapp.verification.enums.VerificationStatus;
import com.doFast.dofastapp.verification.repository.VerificationCaseRepository;
import com.doFast.dofastapp.wallet.enums.WalletTransactionType;
import com.doFast.dofastapp.wallet.service.WalletService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

@Service
public class PayoutDispatchQueue {

    private static final int CLAIM_SCAN_LIMIT = 10;

    private final PayoutRequestRepository payoutRepository;
    private final PayoutEventRepository eventRepository;
    private final UserRepository userRepository;
    private final VerificationCaseRepository verificationRepository;
    private final WalletService walletService;
    private final PayoutProperties properties;

    public PayoutDispatchQueue(
            PayoutRequestRepository payoutRepository,
            PayoutEventRepository eventRepository,
            UserRepository userRepository,
            VerificationCaseRepository verificationRepository,
            WalletService walletService,
            PayoutProperties properties
    ) {
        this.payoutRepository = payoutRepository;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.verificationRepository = verificationRepository;
        this.walletService = walletService;
        this.properties = properties;
    }

    @Transactional
    public Optional<PayoutDispatchCommand> claimNext(String providerCode) {
        LocalDateTime now = LocalDateTime.now();
        for (int scanned = 0; scanned < CLAIM_SCAN_LIMIT; scanned++) {
            Optional<PayoutRequest> candidate = payoutRepository.findNextDispatchableForUpdate(providerCode, now);
            if (candidate.isEmpty()) return Optional.empty();

            PayoutRequest payout = candidate.get();
            boolean active = userRepository.findByIdForUpdate(payout.getUser().getId())
                    .map(user -> user.getStatus() == UserStatus.ACTIVE)
                    .orElse(false);
            boolean verified = verificationRepository.findByUserIdForUpdate(payout.getUser().getId())
                    .map(verification -> verification.getStatus() == VerificationStatus.VERIFIED)
                    .orElse(false);
            if (!active || !verified) {
                payout.requireReview(active ? "IDENTITY_NOT_VERIFIED" : "ACCOUNT_NOT_ACTIVE", now);
                payoutRepository.saveAndFlush(payout);
                record(payout, PayoutEventType.REVIEW_REQUIRED, PayoutEventSource.SYSTEM,
                        "Konto lub weryfikacja nie spełniają już warunków wypłaty. Środki pozostają zarezerwowane do ręcznej decyzji.", now);
                continue;
            }

            payout.startProcessing(now);
            payoutRepository.save(payout);
            record(payout, PayoutEventType.PROCESSING_STARTED, PayoutEventSource.SYSTEM,
                    "Rozpoczęto bezpieczne przekazanie żądania do providera wypłat.", now);
            return Optional.of(new PayoutDispatchCommand(
                    payout.getId(),
                    payout.getUser().getId(),
                    payout.getAmount(),
                    payout.getCurrency(),
                    payout.getProviderCode(),
                    providerIdempotencyKey(payout),
                    payout.getAttemptCount()
            ));
        }
        return Optional.empty();
    }

    @Transactional
    public void recoverOneStaleProcessing() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minus(properties.staleProcessingTimeout());
        payoutRepository.findStaleProcessingForUpdate(cutoff).ifPresent(payout -> {
            if (payout.getAttemptCount() >= properties.maxAttempts()) {
                payout.requireReview("STALE_PROCESSING", now);
                payoutRepository.save(payout);
                record(payout, PayoutEventType.REVIEW_REQUIRED, PayoutEventSource.SYSTEM,
                        "Nie można jednoznacznie potwierdzić wyniku poprzedniej próby. Środki pozostają zarezerwowane.", now);
            } else {
                payout.scheduleRetry("STALE_PROCESSING", now.plus(properties.retryDelay(payout.getAttemptCount())), now);
                payoutRepository.save(payout);
                record(payout, PayoutEventType.RETRY_SCHEDULED, PayoutEventSource.SYSTEM,
                        "Poprzednia próba wygasła; ponowienie użyje tego samego klucza idempotencji providera.", now);
            }
        });
    }

    @Transactional
    public void complete(Long payoutId, PayoutDispatchResult result) {
        PayoutRequest payout = payoutRepository.findByIdForUpdate(payoutId)
                .orElseThrow(() -> new IllegalStateException("Payout disappeared during dispatch"));
        if (payout.getStatus() != PayoutStatus.PROCESSING) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        if (result != null && result.successful() && result.providerReference() != null
                && !result.providerReference().isBlank()) {
            String reference = result.providerReference().trim();
            if (reference.length() > 255) {
                handleRetryableFailure(payout, "PROVIDER_REFERENCE_TOO_LONG", now);
                return;
            }
            if (result.settlementPending()) {
                payout.markSubmitted(reference, now);
                payout.scheduleSubmittedReconciliation(now.plus(properties.submittedReconciliationDelay()));
                payoutRepository.save(payout);
                record(payout, PayoutEventType.SUBMITTED, PayoutEventSource.PROVIDER,
                        "Provider przyjął wypłatę; środki pozostają zarezerwowane do potwierdzenia końcowego rozliczenia.", now);
            } else {
                payout.markPaid(reference, now);
                payoutRepository.save(payout);
                record(payout, PayoutEventType.PAID, PayoutEventSource.PROVIDER,
                        "Provider synchronicznie potwierdził końcowe rozliczenie wypłaty.", now);
            }
            return;
        }

        String code = normalizeFailureCode(result == null ? null : result.failureCode());
        if (result == null || result.retryable()) {
            handleRetryableFailure(payout, code, now);
            return;
        }

        payout.markFailed(code, now);
        payoutRepository.save(payout);
        restoreFunds(payout);
        record(payout, PayoutEventType.FAILED, PayoutEventSource.PROVIDER,
                "Provider definitywnie odrzucił wypłatę.", now);
        record(payout, PayoutEventType.FUNDS_RESTORED, PayoutEventSource.SYSTEM,
                "Dokładnie te same źródła zarezerwowanych środków wróciły do portfela.", now);
    }

    private void handleRetryableFailure(PayoutRequest payout, String code, LocalDateTime now) {
        if (payout.getAttemptCount() >= properties.maxAttempts()) {
            payout.requireReview(code, now);
            payoutRepository.save(payout);
            record(payout, PayoutEventType.REVIEW_REQUIRED, PayoutEventSource.SYSTEM,
                    "Osiągnięto limit automatycznych prób. Środki pozostają zarezerwowane, aby uniknąć podwójnej wypłaty.", now);
            return;
        }
        payout.scheduleRetry(code, now.plus(properties.retryDelay(payout.getAttemptCount())), now);
        payoutRepository.save(payout);
        record(payout, PayoutEventType.RETRY_SCHEDULED, PayoutEventSource.PROVIDER,
                "Provider zgłosił błąd możliwy do ponowienia.", now);
    }

    private void restoreFunds(PayoutRequest payout) {
        boolean restored = walletService.creditRestoringOperation(
                payout.getUser().getId(),
                payout.getAmount(),
                WalletTransactionType.PAYOUT_RESTORE,
                null,
                "payout:" + payout.getId() + ":restore",
                payout.getRequestKey() + ":reserve"
        );
        if (!restored) {
            throw new ConflictException("Wykryto niespójny stan zwrotu zarezerwowanej wypłaty");
        }
    }

    private void record(
            PayoutRequest payout,
            PayoutEventType type,
            PayoutEventSource source,
            String note,
            LocalDateTime now
    ) {
        eventRepository.save(new PayoutEvent(payout, type, source, null, note, now));
    }

    private String providerIdempotencyKey(PayoutRequest payout) {
        return "payout:" + payout.getId() + ":provider";
    }

    private String normalizeFailureCode(String value) {
        if (value == null || value.isBlank()) return "PROVIDER_ERROR";
        String normalized = value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_-]", "_");
        return normalized.length() <= 64 ? normalized : normalized.substring(0, 64);
    }
}
