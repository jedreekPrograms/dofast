package com.doFast.dofastapp.payout.service;

import com.doFast.dofastapp.common.exception.BusinessException;
import com.doFast.dofastapp.payout.config.StripeConnectProperties;
import com.doFast.dofastapp.payout.dto.PayoutOnboardingLinkResponse;
import com.doFast.dofastapp.payout.dto.PayoutOnboardingStatusResponse;
import com.doFast.dofastapp.payout.entity.PayoutRecipientAccount;
import com.doFast.dofastapp.payout.provider.StripeConnectAccountState;
import com.doFast.dofastapp.payout.provider.StripeConnectGateway;
import com.doFast.dofastapp.payout.repository.PayoutRecipientAccountRepository;
import com.doFast.dofastapp.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class StripeConnectOnboardingService {

    public static final String PROVIDER_CODE = "stripe-connect";

    private final StripeConnectProperties properties;
    private final PayoutRecipientAccountRepository repository;
    private final StripeConnectGateway gateway;

    public StripeConnectOnboardingService(
            StripeConnectProperties properties,
            PayoutRecipientAccountRepository repository,
            StripeConnectGateway gateway
    ) {
        this.properties = properties;
        this.repository = repository;
        this.gateway = gateway;
    }

    @Transactional(readOnly = true)
    public PayoutOnboardingStatusResponse cachedStatus(User user) {
        if (!properties.enabled()) return unavailable();
        return repository.findByUser_IdAndProviderCode(user.getId(), PROVIDER_CODE)
                .map(this::toResponse)
                .orElseGet(() -> emptyAvailable());
    }

    @Transactional
    public PayoutOnboardingStatusResponse refreshStatus(User user) {
        requireEnabled();
        PayoutRecipientAccount account = repository.findForUpdate(user.getId(), PROVIDER_CODE).orElse(null);
        if (account == null) return emptyAvailable();
        StripeConnectAccountState state = gateway.retrieveState(account.getProviderAccountId());
        account.synchronize(state.detailsSubmitted(), state.payoutsEnabled(), state.transfersEnabled(),
                state.requirementsDue(), LocalDateTime.now());
        repository.save(account);
        return toResponse(account);
    }

    @Transactional
    public PayoutOnboardingLinkResponse createOnboardingLink(User user) {
        requireEnabled();
        PayoutRecipientAccount account = repository.findForUpdate(user.getId(), PROVIDER_CODE).orElse(null);
        if (account == null) {
            String providerAccountId = gateway.createExpressAccount(
                    user,
                    properties.country(),
                    "dofast:stripe-connect:user:" + user.getId()
            );
            account = new PayoutRecipientAccount();
            account.initialize(user, PROVIDER_CODE, providerAccountId, LocalDateTime.now());
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

    private void requireEnabled() {
        if (!properties.enabled()) throw new BusinessException("Onboarding wypłat Stripe Connect jest wyłączony");
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
