package com.doFast.dofastapp.job.publication;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobPublicationPaymentIntentCleanupQueueTest {

    @Test
    void staleDueCandidateIsRecheckedUnderLockBeforeClaim() {
        JobPublicationRepository repository = mock(JobPublicationRepository.class);
        JobPublication publication = mock(JobPublication.class);
        JobPublicationPaymentIntentCleanupQueue queue = new JobPublicationPaymentIntentCleanupQueue(repository);

        when(repository.findByIdForUpdate(41L)).thenReturn(Optional.of(publication));
        when(publication.getStatus()).thenReturn(JobPublicationStatus.CANCELLED);
        when(publication.getStripePaymentIntentId()).thenReturn("pi_cleanup");
        when(publication.getStripePaymentIntentCleanupNextAttemptAt()).thenReturn(LocalDateTime.now().plusMinutes(1));

        assertThat(queue.claim(41L)).isEmpty();
        verify(publication, org.mockito.Mockito.never()).claimStripePaymentIntentCleanup(any(), any());
    }

    @Test
    void exhaustedCleanupIsQuarantinedInsteadOfCallingStripeAgain() {
        JobPublicationRepository repository = mock(JobPublicationRepository.class);
        JobPublication publication = mock(JobPublication.class);
        JobPublicationPaymentIntentCleanupQueue queue = new JobPublicationPaymentIntentCleanupQueue(repository);

        when(repository.findByIdForUpdate(41L)).thenReturn(Optional.of(publication));
        when(publication.getStatus()).thenReturn(JobPublicationStatus.CANCELLED);
        when(publication.getStripePaymentIntentId()).thenReturn("pi_cleanup");
        when(publication.getStripePaymentIntentCleanupNextAttemptAt()).thenReturn(LocalDateTime.now().minusSeconds(1));
        when(publication.getStripePaymentIntentCleanupAttemptCount())
                .thenReturn(JobPublicationPaymentIntentCleanupQueue.MAX_ATTEMPTS);

        assertThat(queue.claim(41L)).isEmpty();

        verify(publication).requireStripePaymentIntentCleanupReview(
                org.mockito.ArgumentMatchers.eq("MAX_ATTEMPTS_EXCEEDED"),
                any(LocalDateTime.class)
        );
        verify(repository).save(publication);
    }
}
