package com.doFast.dofastapp.payout.provider;

import com.doFast.dofastapp.payment.exception.PaymentProviderException;
import com.doFast.dofastapp.user.entity.User;
import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.AccountLink;
import com.stripe.net.RequestOptions;
import com.stripe.param.AccountCreateParams;
import com.stripe.param.AccountLinkCreateParams;
import org.springframework.stereotype.Component;

@Component
public class StripeConnectGateway {

    public String createExpressAccount(User user, String country, String idempotencyKey) {
        AccountCreateParams.Capabilities capabilities = AccountCreateParams.Capabilities.builder()
                .setTransfers(AccountCreateParams.Capabilities.Transfers.builder()
                        .setRequested(true)
                        .build())
                .build();
        AccountCreateParams params = AccountCreateParams.builder()
                .setType(AccountCreateParams.Type.EXPRESS)
                .setCountry(country)
                .setEmail(user.getEmail())
                .setCapabilities(capabilities)
                .putMetadata("dofastUserId", user.getId().toString())
                .build();
        RequestOptions options = RequestOptions.builder().setIdempotencyKey(idempotencyKey).build();
        try {
            Account account = Account.create(params, options);
            if (account.getId() == null || account.getId().isBlank()) {
                throw new PaymentProviderException("Stripe nie zwrócił identyfikatora konta Connect", null);
            }
            return account.getId();
        } catch (StripeException ex) {
            throw new PaymentProviderException("Nie udało się utworzyć konta wypłat Stripe Connect", ex);
        }
    }

    public String createOnboardingLink(String accountId, String refreshUrl, String returnUrl) {
        AccountLinkCreateParams params = AccountLinkCreateParams.builder()
                .setAccount(accountId)
                .setRefreshUrl(refreshUrl)
                .setReturnUrl(returnUrl)
                .setType(AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING)
                .build();
        try {
            AccountLink link = AccountLink.create(params);
            if (link.getUrl() == null || link.getUrl().isBlank()) {
                throw new PaymentProviderException("Stripe nie zwrócił adresu onboardingu", null);
            }
            return link.getUrl();
        } catch (StripeException ex) {
            throw new PaymentProviderException("Nie udało się przygotować onboardingu Stripe Connect", ex);
        }
    }

    public StripeConnectAccountState retrieveState(String accountId) {
        try {
            Account account = Account.retrieve(accountId);
            boolean transfersEnabled = account.getCapabilities() != null
                    && "active".equals(account.getCapabilities().getTransfers());
            if (account.getCapabilities() == null || account.getCapabilities().getTransfers() == null) {
                transfersEnabled = account.capabilities().getData().stream()
                        .anyMatch(capability -> "transfers".equals(capability.getId())
                                && "active".equals(capability.getStatus()));
            }
            boolean requirementsDue = account.getRequirements() != null
                    && ((account.getRequirements().getCurrentlyDue() != null
                    && !account.getRequirements().getCurrentlyDue().isEmpty())
                    || (account.getRequirements().getPastDue() != null
                    && !account.getRequirements().getPastDue().isEmpty()));
            return new StripeConnectAccountState(
                    Boolean.TRUE.equals(account.getDetailsSubmitted()),
                    Boolean.TRUE.equals(account.getPayoutsEnabled()),
                    transfersEnabled,
                    requirementsDue
            );
        } catch (StripeException ex) {
            throw new PaymentProviderException("Nie udało się odświeżyć statusu konta Stripe Connect", ex);
        }
    }
}
