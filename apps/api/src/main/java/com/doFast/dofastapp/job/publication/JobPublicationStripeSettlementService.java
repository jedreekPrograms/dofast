package com.doFast.dofastapp.job.publication;

import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.job.category.JobCategory;
import com.doFast.dofastapp.job.category.JobCategoryRepository;
import com.doFast.dofastapp.job.dto.JobRequest;
import com.doFast.dofastapp.job.dto.JobResponse;
import com.doFast.dofastapp.job.service.JobService;
import com.doFast.dofastapp.location.routing.entity.RouteQuote;
import com.doFast.dofastapp.location.routing.repository.RouteQuoteRepository;
import com.doFast.dofastapp.payment.service.StripePaymentService;
import com.stripe.model.PaymentIntent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Service
@Transactional
public class JobPublicationStripeSettlementService {

    private final JobPublicationRepository publicationRepository;
    private final JobPublicationService publicationService;
    private final StripePaymentService stripePaymentService;
    private final JobCategoryRepository categoryRepository;
    private final RouteQuoteRepository routeQuoteRepository;
    private final JobService jobService;

    public JobPublicationStripeSettlementService(
            JobPublicationRepository publicationRepository,
            JobPublicationService publicationService,
            StripePaymentService stripePaymentService,
            JobCategoryRepository categoryRepository,
            RouteQuoteRepository routeQuoteRepository,
            JobService jobService
    ) {
        this.publicationRepository = publicationRepository;
        this.publicationService = publicationService;
        this.stripePaymentService = stripePaymentService;
        this.categoryRepository = categoryRepository;
        this.routeQuoteRepository = routeQuoteRepository;
        this.jobService = jobService;
    }

    public boolean processSuccessfulPayment(PaymentIntent paymentIntent, String eventId) {
        Long publicationId = publicationId(paymentIntent);
        JobPublication publication = publicationRepository.findByIdForUpdate(publicationId)
                .orElseThrow(() -> new ConflictException("Płatność wskazuje nieistniejącą publikację"));
        assertPaymentMatches(publication, paymentIntent);

        if (publication.getStripePaymentIntentId() == null) {
            publication.attachStripePaymentIntent(paymentIntent.getId(), LocalDateTime.now());
        }

        boolean newlyProcessed = stripePaymentService.processSuccessfulJobPublicationPayment(
                paymentIntent,
                eventId,
                publication.getId()
        );

        LocalDateTime now = LocalDateTime.now();

        if (publication.getStatus() == JobPublicationStatus.CANCELLED) {
            publication.markLatePaymentAfterCancellation(now);
            return newlyProcessed;
        }
        if (publication.getStatus() == JobPublicationStatus.PUBLISHED
                || publication.getStatus() == JobPublicationStatus.PAYMENT_RECEIVED) {
            publication.recordSuccessfulPayment(now);
            return newlyProcessed;
        }
        if (publication.getStatus() != JobPublicationStatus.PAYMENT_REQUIRED) {
            throw new ConflictException("Publikacja ma nieobsługiwany stan płatności");
        }

        JobPublicationRecoveryReason recoveryReason = settlementBlocker(publication, now);
        if (recoveryReason != null) {
            // Keep PAYMENT_REQUIRED + payment_received_at=NULL while restoring the reservation.
            // Wallet restoration uses saveAndFlush(), which may flush every managed entity in this
            // transaction; publishing a transient PAYMENT_REQUIRED + paid timestamp would violate
            // chk_job_publications_recovery_state before we can move to PAYMENT_RECEIVED.
            publicationService.restoreReservation(publication);
            publication.markPaymentReceived(recoveryReason, now);
            publicationRepository.save(publication);
            return newlyProcessed;
        }

        // The same flush-safety rule applies to the publish path. Job creation/escrow performs
        // wallet writes that may flush the persistence context, so the publication remains in its
        // valid unpaid PAYMENT_REQUIRED representation until all dependent writes have succeeded.
        publicationService.restoreReservation(publication);
        JobRequest request = publicationService.deserialize(publication.getRequestPayload());
        JobResponse job = jobService.createJob(request, publication.getUser());
        publication.markPublished(job.id(), now);
        publication.recordSuccessfulPayment(now);
        publicationRepository.save(publication);
        return newlyProcessed;
    }

    private JobPublicationRecoveryReason settlementBlocker(JobPublication publication, LocalDateTime now) {
        if (!publication.getExpiresAt().isAfter(now)) {
            return JobPublicationRecoveryReason.PUBLICATION_EXPIRED;
        }

        JobCategory category = categoryRepository.findByIdAndActiveTrue(publication.getCategoryId()).orElse(null);
        if (category == null || category.getParent() == null || category.getFulfillmentMode() == null) {
            return JobPublicationRecoveryReason.CATEGORY_UNAVAILABLE;
        }
        if (publication.getRouteQuoteId() == null) {
            return null;
        }

        RouteQuote quote = routeQuoteRepository.findByIdForUpdate(publication.getRouteQuoteId()).orElse(null);
        if (quote == null
                || quote.getConsumedAt() != null
                || !quote.getExpiresAt().isAfter(now)
                || !quote.getUser().getId().equals(publication.getUser().getId())) {
            return JobPublicationRecoveryReason.ROUTE_QUOTE_UNAVAILABLE;
        }
        return null;
    }

    private void assertPaymentMatches(JobPublication publication, PaymentIntent paymentIntent) {
        if (paymentIntent == null || paymentIntent.getId() == null || paymentIntent.getId().isBlank()) {
            throw new ConflictException("Stripe PaymentIntent nie ma identyfikatora");
        }
        String storedIntent = publication.getStripePaymentIntentId();
        if (storedIntent != null && !storedIntent.equals(paymentIntent.getId())) {
            throw new ConflictException("Płatność Stripe nie należy do tej publikacji");
        }
        Map<String, String> metadata = paymentIntent.getMetadata();
        if (metadata == null
                || !JobPublicationPaymentIntentService.PURPOSE.equals(metadata.get("purpose"))
                || !publication.getUser().getId().toString().equals(metadata.get("userId"))) {
            throw new ConflictException("Metadane płatności nie pasują do publikacji");
        }
        Long amountInCents = paymentIntent.getAmount();
        if (amountInCents == null) {
            throw new ConflictException("Stripe PaymentIntent nie zawiera kwoty");
        }
        BigDecimal amount = BigDecimal.valueOf(amountInCents, 2);
        if (amount.compareTo(publication.getPaymentAmount()) != 0
                || paymentIntent.getCurrency() == null
                || !publication.getCurrency().equalsIgnoreCase(paymentIntent.getCurrency())) {
            throw new ConflictException("Kwota lub waluta płatności nie pasuje do publikacji");
        }
    }

    private Long publicationId(PaymentIntent paymentIntent) {
        Map<String, String> metadata = paymentIntent != null ? paymentIntent.getMetadata() : null;
        String value = metadata != null ? metadata.get("jobPublicationId") : null;
        if (value == null || value.isBlank()) {
            throw new ConflictException("Stripe PaymentIntent nie wskazuje publikacji zlecenia");
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new ConflictException("Stripe PaymentIntent zawiera błędny identyfikator publikacji");
        }
    }
}
