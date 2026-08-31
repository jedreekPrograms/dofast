package com.doFast.dofastapp.job.publication;

import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.payment.dto.CreatePaymentIntentResponse;
import com.doFast.dofastapp.payment.exception.PaymentProviderException;
import com.doFast.dofastapp.user.entity.User;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;

@Service
public class JobPublicationPaymentIntentService {

    public static final String PURPOSE = "JOB_PUBLICATION";

    private final JobPublicationPaymentIntentCreateStateService stateService;
    private final JobPublicationPaymentIntentProvider provider;
    private final JobPublicationPaymentIntentCleanupService cleanupService;
    private final JobPublicationService publicationService;

    public JobPublicationPaymentIntentService(
            JobPublicationPaymentIntentCreateStateService stateService,
            JobPublicationPaymentIntentProvider provider,
            JobPublicationPaymentIntentCleanupService cleanupService,
            JobPublicationService publicationService
    ) {
        this.stateService = stateService;
        this.provider = provider;
        this.cleanupService = cleanupService;
        this.publicationService = publicationService;
    }

    /**
     * Persists/claims creation work in a short transaction, calls Stripe after that transaction has
     * committed, then attaches the authoritative provider id in a second transaction. A process
     * crash cannot therefore hold a database row lock across the network call, and the durable
     * create marker lets a retry replay the same Stripe idempotency key safely.
     */
    public CreatePaymentIntentResponse create(Long publicationId, User currentUser) {
        JobPublicationPaymentIntentCreateCommand command = stateService.prepareForOwner(publicationId, currentUser);
        PaymentIntent intent;
        try {
            intent = command.hasExistingPaymentIntent()
                    ? provider.retrieve(command.existingPaymentIntentId())
                    : provider.create(command);
            assertProviderIntentMatches(command, intent);
        } catch (StripeException ex) {
            if (!command.hasExistingPaymentIntent()) {
                stateService.retry(publicationId, "STRIPE_EXCEPTION");
            }
            throw new PaymentProviderException("Nie udało się przygotować płatności za publikację", ex);
        } catch (ConflictException ex) {
            if (!command.hasExistingPaymentIntent()) {
                stateService.quarantine(publicationId, "PROVIDER_IDENTITY_MISMATCH");
            }
            throw ex;
        } catch (RuntimeException ex) {
            if (!command.hasExistingPaymentIntent()) {
                stateService.retry(publicationId, "PROVIDER_RUNTIME_EXCEPTION");
            }
            throw ex;
        }

        JobPublicationPaymentIntentFinalizeStatus finalizeStatus =
                stateService.attachProviderIntent(publicationId, intent.getId());

        if (finalizeStatus == JobPublicationPaymentIntentFinalizeStatus.CANCELLED) {
            cleanupService.process(publicationId);
            throw new ConflictException("Publikacja została anulowana podczas przygotowywania płatności");
        }
        if (finalizeStatus == JobPublicationPaymentIntentFinalizeStatus.EXPIRED) {
            // Close the local reservation immediately when possible. If the process dies between
            // attach and this call, the normal expiry scheduler still converges to CANCELLED and
            // the durable V56 cleanup queue then cancels the attached provider intent.
            publicationService.get(publicationId, currentUser);
            cleanupService.process(publicationId);
            throw new ConflictException("Czas na opłacenie publikacji wygasł");
        }
        if (finalizeStatus == JobPublicationPaymentIntentFinalizeStatus.SETTLED) {
            throw new ConflictException("Płatność została już rozliczona dla tej publikacji");
        }

        String providerStatus = normalizeStatus(intent.getStatus());
        if ("succeeded".equals(providerStatus)) {
            throw new ConflictException("Płatność została już potwierdzona i oczekuje na rozliczenie webhooka");
        }
        if ("canceled".equals(providerStatus)) {
            throw new ConflictException("Płatność Stripe dla tej publikacji została anulowana");
        }
        if (intent.getClientSecret() == null || intent.getClientSecret().isBlank()) {
            throw new PaymentProviderException("Stripe zwrócił niepełną odpowiedź dla publikacji zlecenia", null);
        }

        return new CreatePaymentIntentResponse(
                intent.getId(),
                intent.getClientSecret(),
                command.amount(),
                command.currency()
        );
    }

    static void assertProviderIntentMatches(
            JobPublicationPaymentIntentCreateCommand command,
            PaymentIntent intent
    ) {
        if (intent == null || intent.getId() == null || intent.getId().isBlank()) {
            throw new ConflictException("Stripe PaymentIntent nie ma identyfikatora");
        }
        if (command.hasExistingPaymentIntent()
                && !command.existingPaymentIntentId().equals(intent.getId())) {
            throw new ConflictException("Stripe zwrócił inną płatność niż zapisana dla publikacji");
        }

        Map<String, String> metadata = intent.getMetadata();
        if (metadata == null
                || !PURPOSE.equals(metadata.get("purpose"))
                || !command.userId().toString().equals(metadata.get("userId"))
                || !command.publicationId().toString().equals(metadata.get("jobPublicationId"))) {
            throw new ConflictException("Metadane płatności Stripe nie pasują do publikacji");
        }

        Long amountInCents = intent.getAmount();
        if (amountInCents == null) {
            throw new ConflictException("Stripe PaymentIntent nie zawiera kwoty");
        }
        BigDecimal providerAmount = BigDecimal.valueOf(amountInCents, 2);
        if (providerAmount.compareTo(command.amount()) != 0
                || intent.getCurrency() == null
                || !command.currency().equalsIgnoreCase(intent.getCurrency())) {
            throw new ConflictException("Kwota lub waluta płatności Stripe nie pasuje do publikacji");
        }
    }

    static PaymentIntentCreateParams paymentIntentParams(JobPublicationPaymentIntentCreateCommand command) {
        long amountInCents = command.amount().movePointRight(2).longValueExact();
        return PaymentIntentCreateParams.builder()
                .setAmount(amountInCents)
                .setCurrency(command.currency().toLowerCase(Locale.ROOT))
                .putMetadata("userId", command.userId().toString())
                .putMetadata("purpose", PURPOSE)
                .putMetadata("jobPublicationId", command.publicationId().toString())
                .setAutomaticPaymentMethods(automaticPaymentMethods())
                .build();
    }

    static PaymentIntentCreateParams paymentIntentParams(JobPublication publication) {
        return paymentIntentParams(new JobPublicationPaymentIntentCreateCommand(
                publication.getId(),
                publication.getUser().getId(),
                publication.getPaymentAmount(),
                publication.getCurrency(),
                "dofast:job-publication:" + publication.getId(),
                publication.getStripePaymentIntentId(),
                publication.getStripePaymentIntentCreateAttemptCount()
        ));
    }

    static PaymentIntentCreateParams.AutomaticPaymentMethods automaticPaymentMethods() {
        return PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                .setEnabled(true)
                .build();
    }

    private static String normalizeStatus(String status) {
        return status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
    }
}
