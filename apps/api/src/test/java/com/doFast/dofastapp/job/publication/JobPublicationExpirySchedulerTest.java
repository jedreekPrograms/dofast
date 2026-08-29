package com.doFast.dofastapp.job.publication;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobPublicationExpirySchedulerTest {

    @Test
    void cleansUpStripeIntentOnlyAfterLocalExpiryCommitted() {
        JobPublicationService publicationService = mock(JobPublicationService.class);
        JobPublicationPaymentIntentCleanupService cleanupService = mock(JobPublicationPaymentIntentCleanupService.class);
        JobPublicationExpiryScheduler scheduler = new JobPublicationExpiryScheduler(publicationService, cleanupService);

        when(publicationService.expireOneAndGetId()).thenReturn(41L, 42L, null);

        scheduler.expirePendingPublications();

        var order = inOrder(publicationService, cleanupService);
        order.verify(publicationService).expireOneAndGetId();
        order.verify(cleanupService).cancelAttachedIntentBestEffort(41L);
        order.verify(publicationService).expireOneAndGetId();
        order.verify(cleanupService).cancelAttachedIntentBestEffort(42L);
        order.verify(publicationService).expireOneAndGetId();
        verify(publicationService, times(3)).expireOneAndGetId();
    }

    @Test
    void doesNotCallProviderWhenNothingExpired() {
        JobPublicationService publicationService = mock(JobPublicationService.class);
        JobPublicationPaymentIntentCleanupService cleanupService = mock(JobPublicationPaymentIntentCleanupService.class);
        JobPublicationExpiryScheduler scheduler = new JobPublicationExpiryScheduler(publicationService, cleanupService);

        when(publicationService.expireOneAndGetId()).thenReturn(null);

        scheduler.expirePendingPublications();

        verify(publicationService).expireOneAndGetId();
        verify(cleanupService, times(0)).cancelAttachedIntentBestEffort(org.mockito.ArgumentMatchers.anyLong());
    }
}
