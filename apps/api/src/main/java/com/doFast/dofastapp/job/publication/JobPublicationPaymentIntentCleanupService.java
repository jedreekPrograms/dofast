package com.doFast.dofastapp.job.publication;

import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class JobPublicationPaymentIntentCleanupService {

    private static final Logger log = LoggerFactory.getLogger(JobPublicationPaymentIntentCleanupService.class);
    private static final Set<String> CANCELLABLE_STATUSES = Set.of(
            "requires_payment_method",
            "requires_confirmation",
            "requires_action",
            "requires_capture",
            "processing"
    );

    private final JobPublicationRepository publicationRepository;

    public JobPublicationPaymentIntentCleanupService(JobPublicationRepository publicationRepository) {
        this.publicationRepository = publicationRepository;
    }

    @Transactional(readOnly = true)
    public void cancelAttachedIntentBestEffort(Long publicationId) {
        JobPublication publication = publicationRepository.findById(publicationId).orElse(null);
        if (publication == null || publication.getStatus() != JobPublicationStatus.CANCELLED) {
            return;
        }

        String paymentIntentId = publication.getStripePaymentIntentId();
        if (paymentIntentId == null || paymentIntentId.isBlank()) {
            return;
        }

        try {
            PaymentIntent intent = PaymentIntent.retrieve(paymentIntentId);
            if (intent != null && isCancellableStatus(intent.getStatus())) {
                intent.cancel();
            }
        } catch (StripeException ex) {
            // Local cancellation, reservation release and late-webhook recovery remain authoritative.
            // A transient provider failure must never roll those financial state changes back.
            log.warn("Could not cancel Stripe PaymentIntent {} for cancelled job publication {}",
                    paymentIntentId, publicationId, ex);
        }
    }

    static boolean isCancellableStatus(String status) {
        return status != null && CANCELLABLE_STATUSES.contains(status);
    }
}
