package com.doFast.dofastapp.job.publication;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class JobPublicationPaymentIntentCleanupQueue {

    static final Duration CLAIM_LEASE = Duration.ofMinutes(2);
    static final int MAX_ATTEMPTS = 8;

    private final JobPublicationRepository publicationRepository;

    public JobPublicationPaymentIntentCleanupQueue(JobPublicationRepository publicationRepository) {
        this.publicationRepository = publicationRepository;
    }

    @Transactional(readOnly = true)
    public List<Long> findDueIds(int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 100));
        return publicationRepository.findDueStripePaymentIntentCleanupIds(
                JobPublicationStatus.CANCELLED,
                LocalDateTime.now(),
                PageRequest.of(0, boundedLimit)
        );
    }

    @Transactional
    public Optional<JobPublicationPaymentIntentCleanupCommand> claim(Long publicationId) {
        JobPublication publication = publicationRepository.findByIdForUpdate(publicationId).orElse(null);
        if (publication == null) {
            return Optional.empty();
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dueAt = publication.getStripePaymentIntentCleanupNextAttemptAt();
        boolean due = publication.getStatus() == JobPublicationStatus.CANCELLED
                && publication.getStripePaymentIntentId() != null
                && !publication.getStripePaymentIntentId().isBlank()
                && publication.getPaymentReceivedAt() == null
                && publication.getStripePaymentIntentCleanupCompletedAt() == null
                && !publication.isStripePaymentIntentCleanupReviewRequired()
                && dueAt != null
                && !dueAt.isAfter(now);
        if (!due) {
            return Optional.empty();
        }

        if (publication.getStripePaymentIntentCleanupAttemptCount() >= MAX_ATTEMPTS) {
            publication.requireStripePaymentIntentCleanupReview("MAX_ATTEMPTS_EXCEEDED", now);
            publicationRepository.save(publication);
            return Optional.empty();
        }

        if (!publication.claimStripePaymentIntentCleanup(now, now.plus(CLAIM_LEASE))) {
            return Optional.empty();
        }
        publicationRepository.saveAndFlush(publication);
        return Optional.of(new JobPublicationPaymentIntentCleanupCommand(
                publication.getId(),
                publication.getStripePaymentIntentId(),
                publication.getStripePaymentIntentCleanupAttemptCount()
        ));
    }

    @Transactional
    public void complete(Long publicationId, String providerState) {
        JobPublication publication = publicationRepository.findByIdForUpdate(publicationId).orElse(null);
        if (publication == null || publication.getStripePaymentIntentCleanupCompletedAt() != null) {
            return;
        }
        if (publication.getStatus() != JobPublicationStatus.CANCELLED) {
            return;
        }
        publication.completeStripePaymentIntentCleanup(providerState, LocalDateTime.now());
        publicationRepository.save(publication);
    }

    @Transactional
    public void retry(Long publicationId, String failureCode) {
        JobPublication publication = publicationRepository.findByIdForUpdate(publicationId).orElse(null);
        if (publication == null
                || publication.getStatus() != JobPublicationStatus.CANCELLED
                || publication.getStripePaymentIntentCleanupCompletedAt() != null
                || publication.isStripePaymentIntentCleanupReviewRequired()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        if (publication.getPaymentReceivedAt() != null) {
            publication.completeStripePaymentIntentCleanup("PROVIDER_SUCCEEDED", now);
            publicationRepository.save(publication);
            return;
        }
        if (publication.getStripePaymentIntentCleanupAttemptCount() >= MAX_ATTEMPTS) {
            publication.requireStripePaymentIntentCleanupReview(failureCode, now);
            publicationRepository.save(publication);
            return;
        }

        long delaySeconds = Math.min(300L, 1L << Math.min(publication.getStripePaymentIntentCleanupAttemptCount(), 8));
        publication.retryStripePaymentIntentCleanup(failureCode, now.plusSeconds(delaySeconds), now);
        publicationRepository.save(publication);
    }
}
