package com.doFast.dofastapp.job.publication;

import com.doFast.dofastapp.common.exception.ConflictException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class JobPublicationPaymentIntentCreateRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(JobPublicationPaymentIntentCreateRecoveryService.class);

    private final JobPublicationPaymentIntentCreateStateService stateService;
    private final JobPublicationPaymentIntentProvider provider;
    private final JobPublicationPaymentIntentCleanupService cleanupService;

    public JobPublicationPaymentIntentCreateRecoveryService(
            JobPublicationPaymentIntentCreateStateService stateService,
            JobPublicationPaymentIntentProvider provider,
            JobPublicationPaymentIntentCleanupService cleanupService
    ) {
        this.stateService = stateService;
        this.provider = provider;
        this.cleanupService = cleanupService;
    }

    public List<Long> findDueIds(int limit) {
        return stateService.findDueCancelledRecoveryIds(limit);
    }

    /**
     * Replays the exact original create request only while Stripe's idempotency record is safely
     * expected to exist. If the first provider call never executed, the replay may create a fresh
     * PaymentIntent, but because the publication is already CANCELLED it is immediately attached to
     * the durable cleanup queue and cancelled. If the original call did execute, Stripe returns that
     * same PaymentIntent and the previously orphaned provider object is recovered by id.
     */
    public void process(Long publicationId) {
        JobPublicationPaymentIntentCreateCommand command =
                stateService.claimCancelledRecovery(publicationId).orElse(null);
        if (command == null) {
            return;
        }

        try {
            PaymentIntent intent = provider.create(command);
            JobPublicationPaymentIntentService.assertProviderIntentMatches(command, intent);
            JobPublicationPaymentIntentFinalizeStatus status =
                    stateService.attachProviderIntent(publicationId, intent.getId());
            if (status == JobPublicationPaymentIntentFinalizeStatus.CANCELLED) {
                cleanupService.process(publicationId);
            }
        } catch (StripeException ex) {
            stateService.retry(publicationId, "STRIPE_EXCEPTION");
            log.warn("Could not recover orphan Stripe PaymentIntent creation for publication {} on attempt {}",
                    publicationId, command.attemptCount(), ex);
        } catch (ConflictException ex) {
            stateService.quarantine(publicationId, "PROVIDER_IDENTITY_MISMATCH");
            log.error("Recovered Stripe PaymentIntent did not match publication {}", publicationId, ex);
        } catch (RuntimeException ex) {
            stateService.retry(publicationId, "PROVIDER_RUNTIME_EXCEPTION");
            log.warn("Unexpected orphan PaymentIntent creation recovery failure for publication {} on attempt {}",
                    publicationId, command.attemptCount(), ex);
        }
    }
}
