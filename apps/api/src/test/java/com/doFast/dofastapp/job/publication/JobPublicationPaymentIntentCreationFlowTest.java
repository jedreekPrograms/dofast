package com.doFast.dofastapp.job.publication;

import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.payment.dto.CreatePaymentIntentResponse;
import com.doFast.dofastapp.payment.exception.PaymentProviderException;
import com.doFast.dofastapp.user.entity.User;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobPublicationPaymentIntentCreationFlowTest {

    @Mock private JobPublicationPaymentIntentCreateStateService stateService;
    @Mock private JobPublicationPaymentIntentProvider provider;
    @Mock private JobPublicationPaymentIntentCleanupService cleanupService;
    @Mock private JobPublicationService publicationService;

    private JobPublicationPaymentIntentService service;
    private User owner;
    private JobPublicationPaymentIntentCreateCommand command;

    @BeforeEach
    void setUp() {
        service = new JobPublicationPaymentIntentService(stateService, provider, cleanupService, publicationService);
        owner = new User("owner@example.com", "owner");
        ReflectionTestUtils.setField(owner, "id", 7L);
        command = new JobPublicationPaymentIntentCreateCommand(
                99L,
                7L,
                new BigDecimal("45.00"),
                "PLN",
                "dofast:job-publication:99",
                null,
                1
        );
    }

    @Test
    void providerCallHappensAfterDurablePrepareAndBeforeFinalize() throws Exception {
        PaymentIntent intent = paymentIntent("pi_99", "requires_payment_method");
        when(stateService.prepareForOwner(99L, owner)).thenReturn(command);
        when(provider.create(command)).thenReturn(intent);
        when(stateService.attachProviderIntent(99L, "pi_99"))
                .thenReturn(JobPublicationPaymentIntentFinalizeStatus.READY);

        CreatePaymentIntentResponse response = service.create(99L, owner);

        assertThat(response.paymentIntentId()).isEqualTo("pi_99");
        assertThat(response.clientSecret()).isEqualTo("secret_99");
        assertThat(response.amount()).isEqualByComparingTo("45.00");

        InOrder order = inOrder(stateService, provider);
        order.verify(stateService).prepareForOwner(99L, owner);
        order.verify(provider).create(command);
        order.verify(stateService).attachProviderIntent(99L, "pi_99");
    }

    @Test
    void cancellationWhileStripeCallIsInFlightStillAttachesThenImmediatelyCleansProviderIntent() throws Exception {
        PaymentIntent intent = paymentIntent("pi_after_cancel", "requires_payment_method");
        when(stateService.prepareForOwner(99L, owner)).thenReturn(command);
        when(provider.create(command)).thenReturn(intent);
        when(stateService.attachProviderIntent(99L, "pi_after_cancel"))
                .thenReturn(JobPublicationPaymentIntentFinalizeStatus.CANCELLED);

        assertThatThrownBy(() -> service.create(99L, owner))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("anulowana");

        verify(cleanupService).process(99L);
    }

    @Test
    void providerFailureAfterDurableClaimSchedulesRetryInsteadOfLosingCreateAttempt() throws Exception {
        StripeException stripeException = mock(StripeException.class);
        when(stateService.prepareForOwner(99L, owner)).thenReturn(command);
        when(provider.create(command)).thenThrow(stripeException);

        assertThatThrownBy(() -> service.create(99L, owner))
                .isInstanceOf(PaymentProviderException.class);

        verify(stateService).retry(99L, "STRIPE_EXCEPTION");
        verify(stateService, never()).attachProviderIntent(99L, "pi_99");
    }

    @Test
    void existingAttachedIntentIsRetrievedInsteadOfReplayingCreateRequest() throws Exception {
        JobPublicationPaymentIntentCreateCommand existing = new JobPublicationPaymentIntentCreateCommand(
                99L,
                7L,
                new BigDecimal("45.00"),
                "PLN",
                "dofast:job-publication:99",
                "pi_existing",
                1
        );
        PaymentIntent intent = paymentIntent("pi_existing", "requires_payment_method");
        when(stateService.prepareForOwner(99L, owner)).thenReturn(existing);
        when(provider.retrieve("pi_existing")).thenReturn(intent);
        when(stateService.attachProviderIntent(99L, "pi_existing"))
                .thenReturn(JobPublicationPaymentIntentFinalizeStatus.READY);

        service.create(99L, owner);

        verify(provider).retrieve("pi_existing");
        verify(provider, never()).create(existing);
        verify(stateService, never()).retry(99L, "STRIPE_EXCEPTION");
    }

    @Test
    void mismatchedRecoveredProviderIdentityIsQuarantinedBeforeAttach() throws Exception {
        PaymentIntent intent = paymentIntent("pi_wrong", "requires_payment_method");
        intent.setMetadata(Map.of(
                "userId", "999",
                "purpose", JobPublicationPaymentIntentService.PURPOSE,
                "jobPublicationId", "99"
        ));
        when(stateService.prepareForOwner(99L, owner)).thenReturn(command);
        when(provider.create(command)).thenReturn(intent);

        assertThatThrownBy(() -> service.create(99L, owner))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Metadane");

        verify(stateService).quarantine(99L, "PROVIDER_IDENTITY_MISMATCH");
        verify(stateService, never()).attachProviderIntent(99L, "pi_wrong");
    }

    private PaymentIntent paymentIntent(String id, String status) {
        PaymentIntent intent = new PaymentIntent();
        intent.setId(id);
        intent.setClientSecret("secret_99");
        intent.setAmount(4500L);
        intent.setCurrency("pln");
        intent.setStatus(status);
        intent.setMetadata(Map.of(
                "userId", "7",
                "purpose", JobPublicationPaymentIntentService.PURPOSE,
                "jobPublicationId", "99"
        ));
        return intent;
    }
}
