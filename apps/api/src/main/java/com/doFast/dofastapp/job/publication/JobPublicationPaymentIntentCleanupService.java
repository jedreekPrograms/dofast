package com.doFast.dofastapp.job.publication;

import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
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
    private static final Set<String> TERMINAL_STATUSES = Set.of("canceled", "succeeded");

    private final JobPublicationPaymentIntentCleanupQueue queue;

    public JobPublicationPaymentIntentCleanupService(JobPublicationPaymentIntentCleanupQueue queue) {
        this.queue = queue;
    }

    public List<Long> findDueIds(int limit) {
        return queue.findDueIds(limit);
    }

    /**
     * Claims durable cleanup work in a short database transaction, performs the provider call after
     * the claim transaction has committed, then records success/retry separately. If the process
     * dies anywhere after the claim, the lease expires and another worker can safely re-read the
     * authoritative PaymentIntent state.
     */
    public void process(Long publicationId) {
        JobPublicationPaymentIntentCleanupCommand command = queue.claim(publicationId).orElse(null);
        if (command == null) {
            return;
        }

        try {
            PaymentIntent intent = PaymentIntent.retrieve(command.paymentIntentId());
            if (intent == null) {
                queue.retry(publicationId, "PROVIDER_OBJECT_MISSING");
                return;
            }

            String providerStatus = normalizeStatus(intent.getStatus());
            if (TERMINAL_STATUSES.contains(providerStatus)) {
                queue.complete(publicationId);
                return;
            }
            if (!isCancellableStatus(providerStatus)) {
                queue.retry(publicationId, "UNEXPECTED_PROVIDER_STATUS_" + sanitizeStatus(providerStatus));
                return;
            }

            PaymentIntent cancelled = intent.cancel();
            String cancelledStatus = cancelled == null ? null : normalizeStatus(cancelled.getStatus());
            if (cancelledStatus != null && !"canceled".equals(cancelledStatus)) {
                queue.retry(publicationId, "CANCEL_RETURNED_" + sanitizeStatus(cancelledStatus));
                return;
            }
            queue.complete(publicationId);
        } catch (StripeException ex) {
            queue.retry(publicationId, "STRIPE_EXCEPTION");
            log.warn("Could not clean up Stripe PaymentIntent {} for cancelled job publication {} on attempt {}",
                    command.paymentIntentId(), publicationId, command.attemptCount(), ex);
        } catch (RuntimeException ex) {
            queue.retry(publicationId, "PROVIDER_RUNTIME_EXCEPTION");
            log.warn("Unexpected provider cleanup failure for job publication {} on attempt {}",
                    publicationId, command.attemptCount(), ex);
        }
    }

    static boolean isCancellableStatus(String status) {
        return status != null && CANCELLABLE_STATUSES.contains(normalizeStatus(status));
    }

    private static String normalizeStatus(String status) {
        return status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
    }

    private static String sanitizeStatus(String status) {
        String normalized = status == null || status.isBlank()
                ? "UNKNOWN"
                : status.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_-]", "_");
        return normalized.length() <= 64 ? normalized : normalized.substring(0, 64);
    }
}
