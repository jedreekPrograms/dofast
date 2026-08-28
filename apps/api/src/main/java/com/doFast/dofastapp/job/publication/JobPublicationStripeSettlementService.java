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

        boolean newlyProcessed = stripePaymentService.processSuccessfulPayment(paymentIntent, eventId);

        if (publication.getStatus() == JobPublicationStatus.PUBLISHED
                || publication.getStatus() == JobPublicationStatus.CANCELLED
                || publication.getStatus() == JobPublicationStatus.PAYMENT_RECEIVED) {
            return newlyProcessed;
        }
        if (publication.getStatus() != JobPublicationStatus.PAYMENT_REQUIRED) {
            throw new ConflictException("Publikacja ma nieobsługiwany stan płatności");
        }

        LocalDateTime now = LocalDateTime.now();
        if (!publication.getExpiresAt().isAfter(now) || !lockAndValidateDependencies(publication, now)) {
            publicationService.restoreReservation(publication);
            publication.markPaymentReceived(now);
            publicationRepository.save(publication);
            return newlyProcessed;
        }

        publicationService.restoreReservation(publication);
        JobRequest request = publicationService.deserialize(publication.getRequestPayload());
        JobResponse job = jobService.createJob(request, publication.getUser());
        publication.markPublished(job.id(), now);
        publicationRepository.save(publication);
        return newlyProcessed;
    }

    private boolean lockAndValidateDependencies(JobPublication publication, LocalDateTime now) {
        JobCategory category = categoryRepository.findByIdAndActiveTrue(publication.getCategoryId()).orElse(null);
        if (category == null || category.getParent() == null || category.getFulfillmentMode() == null) {
            return false;
        }
        if (publication.getRouteQuoteId() == null) {
            return true;
        }

        RouteQuote quote = routeQuoteRepository.findByIdForUpdate(publication.getRouteQuoteId()).orElse(null);
        return quote != null
                && quote.getConsumedAt() == null
                && quote.getExpiresAt().isAfter(now)
                && quote.getUser().getId().equals(publication.getUser().getId());
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
