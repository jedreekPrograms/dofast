package com.doFast.dofastapp.job.publication;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobPublicationPaymentIntentCleanupSchedulerTest {

    @Test
    void replaysDueCleanupWorkAfterRestart() {
        JobPublicationPaymentIntentCleanupService cleanupService = mock(JobPublicationPaymentIntentCleanupService.class);
        JobPublicationPaymentIntentCleanupScheduler scheduler = new JobPublicationPaymentIntentCleanupScheduler(cleanupService);
        when(cleanupService.findDueIds(25)).thenReturn(List.of(41L, 42L));

        scheduler.cleanupCancelledPaymentIntents();

        verify(cleanupService).process(41L);
        verify(cleanupService).process(42L);
    }

    @Test
    void oneBrokenCleanupDoesNotBlockRemainingDueWork() {
        JobPublicationPaymentIntentCleanupService cleanupService = mock(JobPublicationPaymentIntentCleanupService.class);
        JobPublicationPaymentIntentCleanupScheduler scheduler = new JobPublicationPaymentIntentCleanupScheduler(cleanupService);
        when(cleanupService.findDueIds(25)).thenReturn(List.of(41L, 42L));
        org.mockito.Mockito.doThrow(new IllegalStateException("boom")).when(cleanupService).process(41L);

        scheduler.cleanupCancelledPaymentIntents();

        verify(cleanupService).process(41L);
        verify(cleanupService).process(42L);
    }
}
