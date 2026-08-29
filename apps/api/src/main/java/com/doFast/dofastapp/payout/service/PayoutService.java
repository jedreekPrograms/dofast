package com.doFast.dofastapp.payout.service;

import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
import com.doFast.dofastapp.payout.config.PayoutProperties;
import com.doFast.dofastapp.payout.dto.CreatePayoutRequest;
import com.doFast.dofastapp.payout.dto.PayoutEligibilityResponse;
import com.doFast.dofastapp.payout.dto.PayoutResponse;
import com.doFast.dofastapp.payout.entity.PayoutEvent;
import com.doFast.dofastapp.payout.entity.PayoutRequest;
import com.doFast.dofastapp.payout.enums.PayoutEventSource;
import com.doFast.dofastapp.payout.enums.PayoutEventType;
import com.doFast.dofastapp.payout.enums.PayoutStatus;
import com.doFast.dofastapp.payout.provider.PayoutProviderRegistry;
import com.doFast.dofastapp.payout.repository.PayoutEventRepository;
import com.doFast.dofastapp.payout.repository.PayoutRequestRepository;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.enums.UserStatus;
import com.doFast.dofastapp.user.repository.UserRepository;
import com.doFast.dofastapp.verification.enums.VerificationStatus;
import com.doFast.dofastapp.verification.repository.VerificationCaseRepository;
import com.doFast.dofastapp.wallet.enums.WalletTransactionType;
import com.doFast.dofastapp.wallet.service.WalletService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class PayoutService {

    private static final String CURRENCY = "PLN";

    private final PayoutRequestRepository payoutRepository;
    private final PayoutEventRepository eventRepository;
    private final UserRepository userRepository;
    private final VerificationCaseRepository verificationRepository;
    private final WalletService walletService;
    private final PayoutProperties properties;
    private final PayoutProviderRegistry providerRegistry;
    private final StripeConnectOnboardingService onboardingService;

    public PayoutService(
            PayoutRequestRepository payoutRepository,
            PayoutEventRepository eventRepository,
            UserRepository userRepository,
            VerificationCaseRepository verificationRepository,
            WalletService walletService,
            PayoutProperties properties,
            PayoutProviderRegistry providerRegistry,
            StripeConnectOnboardingService onboardingService
    ) {
        this.payoutRepository = payoutRepository;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.verificationRepository = verificationRepository;
        this.walletService = walletService;
        this.properties = properties;
        this.providerRegistry = providerRegistry;
        this.onboardingService = onboardingService;
    }

    public PayoutEligibilityResponse eligibility(User currentUser) {
        boolean active = currentUser.getStatus() == UserStatus.ACTIVE;
        boolean verified = verificationRepository.existsByUser_IdAndStatus(currentUser.getId(), VerificationStatus.VERIFIED);
        boolean providerAvailable = providerRegistry.isConfiguredProviderAvailable();
        String configuredProvider = providerRegistry.configuredProviderCode();
        boolean recipientSetupAvailable = onboardingService.setupAvailable();
        boolean recipientReady = recipientSetupAvailable && onboardingService.isRecipientReady(currentUser.getId());
        boolean recipientRequired = StripeConnectOnboardingService.PROVIDER_CODE.equals(configuredProvider);
        boolean recipientRequirementSatisfied = !recipientRequired || recipientReady;
        BigDecimal balance = walletService.getWithdrawableBalance(currentUser.getId());
        return new PayoutEligibilityResponse(
                verified,
                providerAvailable,
                providerRegistry.providerMode(),
                properties.minimumAmount(),
                balance,
                CURRENCY,
                recipientReady,
                recipientSetupAvailable,
                active && verified && providerAvailable && recipientRequirementSatisfied
                        && balance.compareTo(properties.minimumAmount()) >= 0
        );
    }

    public List<PayoutResponse> myPayouts(User currentUser) {
        return payoutRepository.findByUser_IdOrderByRequestedAtDescIdDesc(currentUser.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public PayoutResponse request(CreatePayoutRequest request, User currentUser) {
        BigDecimal amount = properties.normalizeRequestedAmount(request.amount());
        User lockedUser = userRepository.findByIdForUpdate(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Użytkownik nie istnieje"));
        String requestKey = requestKey(lockedUser.getId(), request.requestId());

        PayoutRequest existing = payoutRepository.findByRequestKey(requestKey).orElse(null);
        if (existing != null) {
            if (!existing.getUser().getId().equals(lockedUser.getId())
                    || existing.getAmount().compareTo(amount) != 0) {
                throw new ConflictException("Identyfikator żądania wypłaty został już użyty dla innej operacji");
            }
            return toResponse(existing);
        }

        if (lockedUser.getStatus() != UserStatus.ACTIVE) {
            throw new ForbiddenOperationException("Wypłata nie jest dostępna dla tego konta");
        }
        String providerCode = providerRegistry.providerCodeForNewRequest();
        assertVerifiedForPayout(lockedUser.getId());
        if (StripeConnectOnboardingService.PROVIDER_CODE.equals(providerCode)) {
            if (!onboardingService.setupAvailable()
                    || !onboardingService.refreshAndIsRecipientReady(lockedUser)) {
                throw new ForbiddenOperationException("Dokończ konfigurację konta wypłat przed zleceniem wypłaty");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        PayoutRequest payout = new PayoutRequest();
        payout.initialize(lockedUser, requestKey, amount, CURRENCY, providerCode, now);
        PayoutRequest saved = payoutRepository.saveAndFlush(payout);

        boolean reserved = walletService.debit(
                lockedUser.getId(),
                amount,
                WalletTransactionType.PAYOUT_RESERVE,
                null,
                reserveOperationKey(requestKey)
        );
        if (!reserved) {
            throw new ConflictException("Wykryto niespójny stan rezerwacji wypłaty");
        }

        record(saved, PayoutEventType.REQUESTED, PayoutEventSource.USER, lockedUser,
                "Środki kwalifikujące się do wypłaty zostały zarezerwowane.", now);
        return toResponse(saved);
    }

    @Transactional
    public PayoutResponse cancel(Long payoutId, User currentUser) {
        PayoutRequest payout = payoutRepository.findByIdForUpdate(payoutId)
                .orElseThrow(() -> new ResourceNotFoundException("Wypłata nie istnieje"));
        if (!payout.getUser().getId().equals(currentUser.getId())) {
            throw new ForbiddenOperationException("Nie możesz anulować tej wypłaty");
        }
        if (payout.getStatus() != PayoutStatus.REQUESTED) {
            throw new ConflictException("Można anulować tylko wypłatę oczekującą na rozpoczęcie przetwarzania");
        }

        LocalDateTime now = LocalDateTime.now();
        payout.cancel(now);
        payoutRepository.save(payout);
        restoreFunds(payout);
        record(payout, PayoutEventType.CANCELLED, PayoutEventSource.USER, currentUser,
                "Wypłata została anulowana przez użytkownika.", now);
        record(payout, PayoutEventType.FUNDS_RESTORED, PayoutEventSource.SYSTEM, null,
                "Zarezerwowane źródła środków wróciły do portfela.", now);
        return toResponse(payout);
    }

    private void assertVerifiedForPayout(Long userId) {
        boolean verified = verificationRepository.findByUserIdForUpdate(userId)
                .map(verification -> verification.getStatus() == VerificationStatus.VERIFIED)
                .orElse(false);
        if (!verified) {
            throw new ForbiddenOperationException("Wypłata wymaga zweryfikowanej tożsamości");
        }
    }

    private void restoreFunds(PayoutRequest payout) {
        boolean restored = walletService.creditRestoringOperation(
                payout.getUser().getId(),
                payout.getAmount(),
                WalletTransactionType.PAYOUT_RESTORE,
                null,
                restoreOperationKey(payout),
                reserveOperationKey(payout.getRequestKey())
        );
        if (!restored) {
            throw new ConflictException("Wykryto niespójny stan zwrotu zarezerwowanej wypłaty");
        }
    }

    private void record(
            PayoutRequest payout,
            PayoutEventType type,
            PayoutEventSource source,
            User actor,
            String note,
            LocalDateTime now
    ) {
        eventRepository.save(new PayoutEvent(payout, type, source, actor, note, now));
    }

    private PayoutResponse toResponse(PayoutRequest payout) {
        return new PayoutResponse(
                payout.getId(),
                payout.getAmount(),
                payout.getCurrency(),
                payout.getStatus(),
                providerMode(payout.getProviderCode()),
                payout.getAttemptCount(),
                payout.getRequestedAt(),
                payout.getResolvedAt(),
                payout.getStatus() == PayoutStatus.REQUESTED
        );
    }

    private String requestKey(Long userId, String clientRequestId) {
        return "payout:" + userId + ":client:" + clientRequestId.trim();
    }

    private String reserveOperationKey(String requestKey) {
        return requestKey + ":reserve";
    }

    private String restoreOperationKey(PayoutRequest payout) {
        return "payout:" + payout.getId() + ":restore";
    }

    private String providerMode(String providerCode) {
        return "sandbox".equals(providerCode) ? "SANDBOX" : "LIVE";
    }
}
