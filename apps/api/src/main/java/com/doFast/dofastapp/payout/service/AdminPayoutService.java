package com.doFast.dofastapp.payout.service;

import com.doFast.dofastapp.common.dto.PageResponse;
import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
import com.doFast.dofastapp.payout.dto.AdminPayoutEventResponse;
import com.doFast.dofastapp.payout.dto.AdminPayoutResponse;
import com.doFast.dofastapp.payout.entity.PayoutEvent;
import com.doFast.dofastapp.payout.entity.PayoutRequest;
import com.doFast.dofastapp.payout.enums.PayoutEventSource;
import com.doFast.dofastapp.payout.enums.PayoutEventType;
import com.doFast.dofastapp.payout.enums.PayoutStatus;
import com.doFast.dofastapp.payout.provider.PayoutProviderRegistry;
import com.doFast.dofastapp.payout.repository.PayoutEventRepository;
import com.doFast.dofastapp.payout.repository.PayoutRequestRepository;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.enums.UserRole;
import com.doFast.dofastapp.wallet.enums.WalletTransactionType;
import com.doFast.dofastapp.wallet.service.WalletService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class AdminPayoutService {

    private final PayoutRequestRepository payoutRepository;
    private final PayoutEventRepository eventRepository;
    private final WalletService walletService;
    private final PayoutProviderRegistry providerRegistry;

    public AdminPayoutService(
            PayoutRequestRepository payoutRepository,
            PayoutEventRepository eventRepository,
            WalletService walletService,
            PayoutProviderRegistry providerRegistry
    ) {
        this.payoutRepository = payoutRepository;
        this.eventRepository = eventRepository;
        this.walletService = walletService;
        this.providerRegistry = providerRegistry;
    }

    public PageResponse<AdminPayoutResponse> list(PayoutStatus status, int page, int size, User admin) {
        assertAdmin(admin);
        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("requestedAt"), Sort.Order.desc("id"))
        );
        Page<PayoutRequest> result = status == null
                ? payoutRepository.findAll(pageable)
                : payoutRepository.findByStatus(status, pageable);
        return PageResponse.from(result, result.getContent().stream().map(this::toAdminResponse).toList());
    }

    public List<AdminPayoutEventResponse> events(Long payoutId, User admin) {
        assertAdmin(admin);
        if (!payoutRepository.existsById(payoutId)) {
            throw new ResourceNotFoundException("Wypłata nie istnieje");
        }
        return eventRepository.findByPayout_IdOrderByCreatedAtAscIdAsc(payoutId)
                .stream()
                .map(event -> new AdminPayoutEventResponse(
                        event.getId(),
                        event.getEventType(),
                        event.getSource(),
                        event.getActor() != null ? event.getActor().getId() : null,
                        event.getActor() != null ? event.getActor().getNickname() : null,
                        event.getNote(),
                        event.getCreatedAt()
                ))
                .toList();
    }

    @Transactional
    public AdminPayoutResponse retry(Long payoutId, User admin) {
        assertAdmin(admin);
        PayoutRequest payout = getForUpdate(payoutId);
        if (payout.getStatus() != PayoutStatus.REVIEW_REQUIRED) {
            throw new ConflictException("Ponowić można tylko wypłatę wymagającą ręcznej weryfikacji");
        }
        requireLocallyResolvableReview(payout);
        if (!providerRegistry.isProviderAvailable(payout.getProviderCode())) {
            throw new ConflictException("Provider tej wypłaty nie jest obecnie dostępny; zakończ ręczną weryfikację zamiast ponawiać");
        }
        LocalDateTime now = LocalDateTime.now();
        payout.retryByAdmin(now);
        payoutRepository.save(payout);
        eventRepository.save(new PayoutEvent(
                payout,
                PayoutEventType.ADMIN_RETRY,
                PayoutEventSource.ADMIN,
                admin,
                "Administrator dopuścił jedną kolejną próbę z tym samym kluczem idempotencji providera.",
                now
        ));
        return toAdminResponse(payout);
    }

    @Transactional
    public AdminPayoutResponse failAndRestore(Long payoutId, String reason, User admin) {
        assertAdmin(admin);
        PayoutRequest payout = getForUpdate(payoutId);
        if (payout.getStatus() != PayoutStatus.REVIEW_REQUIRED) {
            throw new ConflictException("Definitywnie odrzucić można tylko wypłatę wymagającą ręcznej weryfikacji");
        }
        requireLocallyResolvableReview(payout);
        LocalDateTime now = LocalDateTime.now();
        payout.markFailed("ADMIN_DECLINED", now);
        payoutRepository.save(payout);
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
        eventRepository.save(new PayoutEvent(
                payout,
                PayoutEventType.FAILED,
                PayoutEventSource.ADMIN,
                admin,
                reason.trim(),
                now
        ));
        eventRepository.save(new PayoutEvent(
                payout,
                PayoutEventType.FUNDS_RESTORED,
                PayoutEventSource.SYSTEM,
                null,
                "Dokładnie te same źródła zarezerwowanych środków wróciły do portfela po decyzji administratora.",
                now
        ));
        return toAdminResponse(payout);
    }

    private void requireLocallyResolvableReview(PayoutRequest payout) {
        if (PayoutProviderSafetyPolicy.requiresExternalProviderReconciliation(payout)) {
            throw new ConflictException(
                    "Nie można ponowić ani zwrócić środków: poprzedni Stripe Transfer/Payout może już istnieć, a bezpieczne okno idempotencji wygasło. Najpierw potwierdź stan operacji po stronie Stripe."
            );
        }
    }

    private PayoutRequest getForUpdate(Long payoutId) {
        return payoutRepository.findByIdForUpdate(payoutId)
                .orElseThrow(() -> new ResourceNotFoundException("Wypłata nie istnieje"));
    }

    private AdminPayoutResponse toAdminResponse(PayoutRequest payout) {
        return new AdminPayoutResponse(
                payout.getId(),
                payout.getUser().getId(),
                payout.getUser().getNickname(),
                payout.getAmount(),
                payout.getCurrency(),
                payout.getStatus(),
                payout.getProviderCode(),
                payout.getProviderReference(),
                payout.getProviderTransferReference(),
                payout.getAttemptCount(),
                payout.getFailureCode(),
                payout.getRequestedAt(),
                payout.getProcessingStartedAt(),
                payout.getResolvedAt()
        );
    }

    private void assertAdmin(User user) {
        if (user == null || user.getRole() != UserRole.ADMIN) {
            throw new ForbiddenOperationException("Ta operacja wymaga uprawnień administratora");
        }
    }
}
