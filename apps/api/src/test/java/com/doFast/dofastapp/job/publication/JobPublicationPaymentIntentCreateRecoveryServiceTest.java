package com.doFast.dofastapp.job.publication;

import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobPublicationPaymentIntentCreateRecoveryServiceTest {

    @Mock private JobPublicationPaymentIntentCreateStateService stateService;
    @Mock private JobPublicationPaymentIntentProvider provider;
    @Mock private JobPublicationPaymentIntentCleanupService cleanupService;

    private JobPublicationPaymentIntentCreateRecoveryService service;
    private JobPublicationPaymentIntentCreateCommand command;

    @BeforeEach
    void setUp() {
        service = new JobPublicationPaymentIntentCreateRecoveryService(stateService, provider, cleanupService);
        command = new JobPublicationPaymentIntentCreateCommand(
                99L,
                7L,
                new BigDecimal("45.00"),
                "PLN",
                "dofast:job-publication:99",
                null,
                2
        );
    }

    @Test
    void recoveredProviderIntentIsAttachedBeforeDurableCancellationCleanup() throws Exception {
        PaymentIntent intent = paymentIntent("pi_recovered");
        when(stateService.claimCancelledRecovery(99L)).thenReturn(Optional.of(command));
        when(provider.create(command)).thenReturn(intent);
        when(stateService.attachProviderIntent(99L, "pi_recovered"))
                .thenReturn(JobPublicationPaymentIntentFinalizeStatus.CANCELLED);

        service.process(99L);

        verify(stateService).attachProviderIntent(99L, "pi_recovered");
        verify(cleanupService).process(99L);
    }

    @Test
    void providerFailureKeepsOrphanRecoveryRetryable() throws Exception {
        StripeException stripeException = mock(StripeException.class);
        when(stateService.claimCancelledRecovery(99L)).thenReturn(Optional.of(command));
        when(provider.create(command)).thenThrow(stripeException);

        service.process(99L);

        verify(stateService).retry(99L, "STRIPE_EXCEPTION");
        verify(cleanupService, never()).process(99L);
    }

    @Test
    void providerIdentityMismatchIsQuarantinedInsteadOfAttached() throws Exception {
        PaymentIntent intent = paymentIntent("pi_wrong_owner");
        intent.setMetadata(Map.of(
                "userId", "1234",
                "purpose", JobPublicationPaymentIntentService.PURPOSE,
                "jobPublicationId", "99"
        ));
        when(stateService.claimCancelledRecovery(99L)).thenReturn(Optional.of(command));
        when(provider.create(command)).thenReturn(intent);

        service.process(99L);

        verify(stateService).quarantine(99L, "PROVIDER_IDENTITY_MISMATCH");
        verify(stateService, never()).attachProviderIntent(99L, "pi_wrong_owner");
        verify(cleanupService, never()).process(99L);
    }

    private PaymentIntent paymentIntent(String id) {
        PaymentIntent intent = new PaymentIntent();
        intent.setId(id);
        intent.setAmount(4500L);
        intent.setCurrency("pln");
        intent.setStatus("requires_payment_method");
        intent.setMetadata(Map.of(
                "userId", "7",
                "purpose", JobPublicationPaymentIntentService.PURPOSE,
                "jobPublicationId", "99"
        ));
        return intent;
    }
}
