package com.doFast.dofastapp.payout.service;

import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
import com.doFast.dofastapp.payout.entity.PayoutEvent;
import com.doFast.dofastapp.payout.entity.PayoutProviderEvent;
import com.doFast.dofastapp.payout.entity.PayoutRequest;
import com.doFast.dofastapp.payout.enums.PayoutEventSource;
import com.doFast.dofastapp.payout.enums.PayoutEventType;
import com.doFast.dofastapp.payout.enums.PayoutStatus;
import com.doFast.dofastapp.payout.provider.PayoutProviderSettlementCommand;
import com.doFast.dofastapp.payout.provider.PayoutProviderSettlementOutcome;
import com.doFast.dofastapp.payout.provider.PayoutProviderSettlementResult;
import com.doFast.dofastapp.payout.repository.PayoutEventRepository;
import com.doFast.dofastapp.payout.repository.PayoutProviderEventRepository;
import com.doFast.dofastapp.payout.repository.PayoutRequestRepository;
import com.doFast.dofastapp.wallet.enums.WalletTransactionType;
import com.doFast.dofastapp.wallet.service.WalletService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class PayoutProviderSettlementService {

    private final PayoutRequestRepository payoutRepository;
    private final PayoutProviderEventRepository providerEventRepository;
    private final PayoutEventRepository eventRepository;
    private final WalletService walletService;

    public PayoutProviderSettlementService(
            PayoutRequestRepository payoutRepository,
            PayoutProviderEventRepository providerEventRepository,
            PayoutEventRepository eventRepository,
            WalletService walletService
    ) {
        this.payoutRepository = payoutRepository;
        this.providerEventRepository = providerEventRepository;
        this.eventRepository = eventRepository;
        this.walletService = walletService;
    }

    @Transactional
    public PayoutProviderSettlementResult settle(PayoutProviderSettlementCommand command) {
        NormalizedSettlement settlement = normalize(command);
        PayoutRequest payout = payoutRepository.findByProviderReferenceForUpdate(
                        settlement.providerCode(), settlement.providerReference())
                .orElseThrow(() -> new ResourceNotFoundException("Wypłata dla referencji providera nie istnieje"));

        if (providerEventRepository.existsByProviderCodeAndProviderEventId(
                settlement.providerCode(), settlement.providerEventId())) {
            return PayoutProviderSettlementResult.DUPLICATE;
        }

        LocalDateTime now = LocalDateTime.now();
        PayoutProviderSettlementResult result;
        if (payout.getStatus() == PayoutStatus.SUBMITTED) {
            if (settlement.outcome() == PayoutProviderSettlementOutcome.PAID) {
                payout.markSubmittedPaid(now);
                payoutRepository.save(payout);
                eventRepository.save(new PayoutEvent(
                        payout,
                        PayoutEventType.PAID,
                        PayoutEventSource.PROVIDER,
                        null,
                        "Provider potwierdził końcowe rozliczenie wcześniej przyjętej wypłaty.",
                        now
                ));
            } else {
                payout.markFailed(settlement.failureCode(), now);
                payoutRepository.save(payout);
                restoreFunds(payout);
                eventRepository.save(new PayoutEvent(
                        payout,
                        PayoutEventType.FAILED,
                        PayoutEventSource.PROVIDER,
                        null,
                        "Provider potwierdził definitywne niepowodzenie wcześniej przyjętej wypłaty.",
                        now
                ));
                eventRepository.save(new PayoutEvent(
                        payout,
                        PayoutEventType.FUNDS_RESTORED,
                        PayoutEventSource.SYSTEM,
                        null,
                        "Dokładnie te same źródła zarezerwowanych środków wróciły do portfela.",
                        now
                ));
            }
            result = PayoutProviderSettlementResult.APPLIED;
        } else if ((payout.getStatus() == PayoutStatus.PAID
                && settlement.outcome() == PayoutProviderSettlementOutcome.PAID)
                || (payout.getStatus() == PayoutStatus.FAILED
                && settlement.outcome() == PayoutProviderSettlementOutcome.FAILED)) {
            result = PayoutProviderSettlementResult.ALREADY_SETTLED;
        } else {
            throw new ConflictException("Zdarzenie providera jest sprzeczne z aktualnym stanem wypłaty");
        }

        providerEventRepository.save(new PayoutProviderEvent(
                payout,
                settlement.providerCode(),
                settlement.providerEventId(),
                settlement.providerReference(),
                settlement.outcome(),
                settlement.failureCode(),
                now
        ));
        return result;
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

    private NormalizedSettlement normalize(PayoutProviderSettlementCommand command) {
        if (command == null || command.outcome() == null) {
            throw new IllegalArgumentException("Provider settlement outcome is required");
        }
        String providerCode = normalizeRequired(command.providerCode(), 32, true, "provider code");
        String providerEventId = normalizeRequired(command.providerEventId(), 255, false, "provider event id");
        String providerReference = normalizeRequired(command.providerReference(), 255, false, "provider reference");
        String failureCode = command.outcome() == PayoutProviderSettlementOutcome.FAILED
                ? normalizeFailureCode(command.failureCode())
                : null;
        return new NormalizedSettlement(
                providerCode,
                providerEventId,
                providerReference,
                command.outcome(),
                failureCode
        );
    }

    private String normalizeRequired(String value, int maxLength, boolean lowerCase, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing " + label);
        }
        String normalized = value.trim();
        if (lowerCase) normalized = normalized.toLowerCase(Locale.ROOT);
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(label + " is too long");
        }
        return normalized;
    }

    private String normalizeFailureCode(String value) {
        if (value == null || value.isBlank()) return "PROVIDER_FAILED";
        String normalized = value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_-]", "_");
        return normalized.length() <= 64 ? normalized : normalized.substring(0, 64);
    }

    private record NormalizedSettlement(
            String providerCode,
            String providerEventId,
            String providerReference,
            PayoutProviderSettlementOutcome outcome,
            String failureCode
    ) {}
}
