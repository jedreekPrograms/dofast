package com.doFast.dofastapp.payout.service;

import com.doFast.dofastapp.common.exception.BusinessException;
import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
import com.doFast.dofastapp.payout.config.StripeConnectProperties;
import com.doFast.dofastapp.payout.dto.PayoutOnboardingLinkResponse;
import com.doFast.dofastapp.payout.dto.PayoutOnboardingStatusResponse;
import com.doFast.dofastapp.payout.entity.PayoutRecipientAccount;
import com.doFast.dofastapp.payout.provider.StripeConnectAccountState;
import com.doFast.dofastapp.payout.provider.StripeConnectGateway;
import com.doFast.dofastapp.payout.repository.PayoutRecipientAccountRepository;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.enums.UserStatus;
import com.doFast.dofastapp.user.repository.UserRepository;
import com.doFast.dofastapp.verification.enums.VerificationStatus;
import com.doFast.dofastapp.verification.repository.VerificationCaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class StripeConnectOnboardingService {

    public static final String PROVIDER_CODE = "stripe-connect";

    private final StripeConnectProperties properties;
    private final PayoutRecipientAccountRepository repository;
    private final StripeConnectGateway gateway;
    private final VerificationCaseRepository verificationRepository;
    private final UserRepository userRepository;

    public StripeConnectOnboardingService(
            StripeConnectProperties properties,
            PayoutRecipientAccountRepository repository,
            StripeConnectGateway gateway,
            VerificationCaseRepository verificationRepository,
            UserRepository userRepository
    ) {
        this.properties = properties;
        this.repository = repository;
        this.gateway = gateway;
        this.verificationRepository = verificationRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public PayoutOnboardingStatusResponse cachedStatus(User user) {
        if (!properties.enabled()) return unavailable();
        return repository.findByUser_IdAndProviderCode(user.getId(), PROVIDER_CODE)
                .map(this::toResponse)
                .orElseGet(this::emptyAvailable);
    }

    @Transactional
    public PayoutOnboardingStatusResponse refreshStatus(User user) {
        requireEnabled();
        PayoutRecipientAccount account = refreshAccount(user.getId());
        return account == null ? emptyAvailable() : toResponse(account);
    }

    @Transactional
    public boolean refreshAndIsRecipientReady(User user) {
        requireEnabled();
        PayoutRecipientAccount account = refreshAccount(user.getId());
        return account != null && account.readyForPayout();
    }

    @Transactional
    public PayoutOnboardingLinkResponse createOnboardingLink(User user) {
        requireEnabled();
        if (user == null || user.getId() == null) {
            throw new ForbiddenOperationException("Konfiguracja wypłat nie jest dostępna dla tego konta");
        }
        User lockedUser = userRepository.findByIdForUpdate(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Użytkownik nie istnieje"));
        requireEligibleForProvisioning(lockedUser);

        PayoutRecipientAccount account = repository.findForUpdate(lockedUser.getId(), PROVIDER_CODE).orElse(null);
        if (account == null) {
            String providerAccountId = gateway.createExpressAccount(
                    lockedUser,
                    properties.country(),
                    "dofast:stripe-connect:user:" + lockedUser.getId()
            );
            account = new PayoutRecipientAccount();
            account.initialize(lockedUser, PROVIDER_CODE, providerAccountId, LocalDateTime.now());
            account = repository.saveAndFlush(account);
        }
        String url = gateway.createOnboardingLink(
                account.getProviderAccountId(),
                properties.refreshUrl(),
                properties.returnUrl()
        );
        return new PayoutOnboardingLinkResponse(url);
    }

    @Transactional(readOnly = true)
    public boolean isRecipientReady(Long userId) {
        return repository.findByUser_IdAndProviderCode(userId, PROVIDER_CODE)
                .map(PayoutRecipientAccount::readyForPayout)
                .orElse(false);
    }

    public boolean setupAvailable() {
        return properties.enabled();
    }

    private PayoutRecipientAccount refreshAccount(Long userId) {
        PayoutRecipientAccount account = repository.findForUpdate(userId, PROVIDER_CODE).orElse(null);
        if (account == null) return null;
        StripeConnectAccountState state = gateway.retrieveState(account.getProviderAccountId());
        account.synchronize(state.detailsSubmitted(), state.payoutsEnabled(), state.transfersEnabled(),
                state.requirementsDue(), LocalDateTime.now());
        return repository.save(account);
    }

    private void requireEnabled() {
        if (!properties.enabled()) throw new BusinessException("Onboarding wypłat Stripe Connect jest wyłączony");
    }

    private void requireEligibleForProvisioning(User user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ForbiddenOperationException("Konfiguracja wypłat nie jest dostępna dla tego konta");
        }
        if (!verificationRepository.existsByUser_IdAndStatus(user.getId(), VerificationStatus.VERIFIED)) {
            throw new ForbiddenOperationException("Najpierw ukończ weryfikację tożsamości");
        }
    }

    private PayoutOnboardingStatusResponse toResponse(PayoutRecipientAccount account) {
        return new PayoutOnboardingStatusResponse(
                true,
                true,
                account.isDetailsSubmitted(),
                account.isPayoutsEnabled(),
                account.isTransfersEnabled(),
                account.isRequirementsDue(),
                account.readyForPayout(),
                account.getLastSyncedAt()
        );
    }

    private PayoutOnboardingStatusResponse emptyAvailable() {
        return new PayoutOnboardingStatusResponse(true, false, false, false, false, true, false, null);
    }

    private PayoutOnboardingStatusResponse unavailable() {
        return new PayoutOnboardingStatusResponse(false, false, false, false, false, true, false, null);
    }
}
