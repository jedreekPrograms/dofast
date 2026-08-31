package com.doFast.dofastapp.job.publication;

import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
import com.doFast.dofastapp.user.entity.User;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class JobPublicationPaymentIntentCreateStateService {

    static final Duration CLAIM_LEASE = Duration.ofMinutes(2);
    static final Duration SAFE_IDEMPOTENCY_REPLAY_WINDOW = Duration.ofHours(23);
    static final int MAX_ATTEMPTS = 8;

    private final JobPublicationRepository publicationRepository;

    public JobPublicationPaymentIntentCreateStateService(JobPublicationRepository publicationRepository) {
        this.publicationRepository = publicationRepository;
    }

    @Transactional
    public JobPublicationPaymentIntentCreateCommand prepareForOwner(Long publicationId, User currentUser) {
        JobPublication publication = publicationRepository.findByIdForUpdate(publicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Publikacja nie istnieje"));
        assertOwner(publication, currentUser);

        LocalDateTime now = LocalDateTime.now();
        if (publication.getStatus() != JobPublicationStatus.PAYMENT_REQUIRED) {
            throw new ConflictException("Ta publikacja nie oczekuje już na płatność");
        }
        if (!publication.getExpiresAt().isAfter(now)) {
            throw new ConflictException("Czas na opłacenie publikacji wygasł");
        }

        if (hasText(publication.getStripePaymentIntentId())) {
            return command(publication, publication.getStripePaymentIntentId());
        }

        assertCreateCanBeRetried(publication, now);
        if (!publication.claimStripePaymentIntentCreate(now, now.plus(CLAIM_LEASE))) {
            throw new ConflictException("Płatność jest już przygotowywana. Spróbuj ponownie za chwilę");
        }
        publicationRepository.saveAndFlush(publication);
        return command(publication, null);
    }

    @Transactional(readOnly = true)
    public List<Long> findDueCancelledRecoveryIds(int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 100));
        return publicationRepository.findDueStripePaymentIntentCreateRecoveryIds(
                JobPublicationStatus.CANCELLED,
                LocalDateTime.now(),
                PageRequest.of(0, boundedLimit)
        );
    }

    @Transactional
    public Optional<JobPublicationPaymentIntentCreateCommand> claimCancelledRecovery(Long publicationId) {
        JobPublication publication = publicationRepository.findByIdForUpdate(publicationId).orElse(null);
        if (publication == null) {
            return Optional.empty();
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dueAt = publication.getStripePaymentIntentCreateNextAttemptAt();
        boolean due = publication.getStatus() == JobPublicationStatus.CANCELLED
                && !hasText(publication.getStripePaymentIntentId())
                && publication.getStripePaymentIntentCreateStartedAt() != null
                && publication.getPaymentReceivedAt() == null
                && !publication.isStripePaymentIntentCreateReviewRequired()
                && dueAt != null
                && !dueAt.isAfter(now);
        if (!due) {
            return Optional.empty();
        }

        if (!isWithinSafeReplayWindow(publication, now)) {
            publication.requireStripePaymentIntentCreateReview("IDEMPOTENCY_WINDOW_EXPIRED", now);
            publicationRepository.save(publication);
            return Optional.empty();
        }
        if (publication.getStripePaymentIntentCreateAttemptCount() >= MAX_ATTEMPTS) {
            publication.requireStripePaymentIntentCreateReview("MAX_ATTEMPTS_EXCEEDED", now);
            publicationRepository.save(publication);
            return Optional.empty();
        }
        if (!publication.claimStripePaymentIntentCreate(now, now.plus(CLAIM_LEASE))) {
            return Optional.empty();
        }

        publicationRepository.saveAndFlush(publication);
        return Optional.of(command(publication, null));
    }

    @Transactional
    public JobPublicationPaymentIntentFinalizeStatus attachProviderIntent(Long publicationId, String paymentIntentId) {
        JobPublication publication = publicationRepository.findByIdForUpdate(publicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Publikacja nie istnieje"));
        LocalDateTime now = LocalDateTime.now();

        String existing = publication.getStripePaymentIntentId();
        if (hasText(existing) && !existing.equals(paymentIntentId)) {
            throw new ConflictException("Publikacja ma już inną płatność Stripe");
        }
        if (!hasText(existing)) {
            publication.attachStripePaymentIntent(paymentIntentId, now);
            publicationRepository.saveAndFlush(publication);
        }

        if (publication.getStatus() == JobPublicationStatus.CANCELLED) {
            return JobPublicationPaymentIntentFinalizeStatus.CANCELLED;
        }
        if (publication.getStatus() == JobPublicationStatus.PUBLISHED
                || publication.getStatus() == JobPublicationStatus.PAYMENT_RECEIVED
                || publication.getPaymentReceivedAt() != null) {
            return JobPublicationPaymentIntentFinalizeStatus.SETTLED;
        }
        if (publication.getStatus() == JobPublicationStatus.PAYMENT_REQUIRED) {
            return publication.getExpiresAt().isAfter(now)
                    ? JobPublicationPaymentIntentFinalizeStatus.READY
                    : JobPublicationPaymentIntentFinalizeStatus.EXPIRED;
        }
        return JobPublicationPaymentIntentFinalizeStatus.SETTLED;
    }

    @Transactional
    public void retry(Long publicationId, String failureCode) {
        JobPublication publication = publicationRepository.findByIdForUpdate(publicationId).orElse(null);
        if (publication == null
                || hasText(publication.getStripePaymentIntentId())
                || publication.getPaymentReceivedAt() != null
                || publication.isStripePaymentIntentCreateReviewRequired()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        if (!isWithinSafeReplayWindow(publication, now)) {
            publication.requireStripePaymentIntentCreateReview("IDEMPOTENCY_WINDOW_EXPIRED", now);
            publicationRepository.save(publication);
            return;
        }
        if (publication.getStripePaymentIntentCreateAttemptCount() >= MAX_ATTEMPTS) {
            publication.requireStripePaymentIntentCreateReview(failureCode, now);
            publicationRepository.save(publication);
            return;
        }

        long delaySeconds = Math.min(300L, 1L << Math.min(publication.getStripePaymentIntentCreateAttemptCount(), 8));
        publication.retryStripePaymentIntentCreate(failureCode, now.plusSeconds(delaySeconds), now);
        publicationRepository.save(publication);
    }

    @Transactional
    public void quarantine(Long publicationId, String failureCode) {
        JobPublication publication = publicationRepository.findByIdForUpdate(publicationId).orElse(null);
        if (publication == null || hasText(publication.getStripePaymentIntentId())) {
            return;
        }
        publication.requireStripePaymentIntentCreateReview(failureCode, LocalDateTime.now());
        publicationRepository.save(publication);
    }

    private void assertCreateCanBeRetried(JobPublication publication, LocalDateTime now) {
        if (publication.isStripePaymentIntentCreateReviewRequired()) {
            throw new ConflictException("Płatność wymaga ręcznej weryfikacji przed ponowną próbą");
        }
        if (!isWithinSafeReplayWindow(publication, now)) {
            publication.requireStripePaymentIntentCreateReview("IDEMPOTENCY_WINDOW_EXPIRED", now);
            publicationRepository.save(publication);
            throw new ConflictException("Nie można już bezpiecznie ponowić tworzenia płatności");
        }
        if (publication.getStripePaymentIntentCreateAttemptCount() >= MAX_ATTEMPTS) {
            publication.requireStripePaymentIntentCreateReview("MAX_ATTEMPTS_EXCEEDED", now);
            publicationRepository.save(publication);
            throw new ConflictException("Płatność wymaga ręcznej weryfikacji przed ponowną próbą");
        }
        LocalDateTime dueAt = publication.getStripePaymentIntentCreateNextAttemptAt();
        if (dueAt != null && dueAt.isAfter(now)) {
            throw new ConflictException("Płatność jest już przygotowywana. Spróbuj ponownie za chwilę");
        }
    }

    private boolean isWithinSafeReplayWindow(JobPublication publication, LocalDateTime now) {
        LocalDateTime startedAt = publication.getStripePaymentIntentCreateStartedAt();
        return startedAt == null || now.isBefore(startedAt.plus(SAFE_IDEMPOTENCY_REPLAY_WINDOW));
    }

    private JobPublicationPaymentIntentCreateCommand command(JobPublication publication, String existingIntentId) {
        return new JobPublicationPaymentIntentCreateCommand(
                publication.getId(),
                publication.getUser().getId(),
                publication.getPaymentAmount(),
                publication.getCurrency(),
                "dofast:job-publication:" + publication.getId(),
                existingIntentId,
                publication.getStripePaymentIntentCreateAttemptCount()
        );
    }

    private void assertOwner(JobPublication publication, User currentUser) {
        if (currentUser == null || currentUser.getId() == null
                || !currentUser.getId().equals(publication.getUser().getId())) {
            throw new ForbiddenOperationException("Ta publikacja należy do innego użytkownika");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
