package com.doFast.dofastapp.job.publication;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobPublicationPaymentIntentCreateRecoverySchedulerTest {

    @Test
    void replaysDueOrphanCreateWorkAfterRestart() {
        JobPublicationPaymentIntentCreateRecoveryService recoveryService =
                mock(JobPublicationPaymentIntentCreateRecoveryService.class);
        JobPublicationPaymentIntentCreateRecoveryScheduler scheduler =
                new JobPublicationPaymentIntentCreateRecoveryScheduler(recoveryService);
        when(recoveryService.findDueIds(25)).thenReturn(List.of(51L, 52L));

        scheduler.recoverCancelledOrphanPaymentIntents();

        verify(recoveryService).process(51L);
        verify(recoveryService).process(52L);
    }

    @Test
    void oneBrokenRecoveryDoesNotBlockRemainingDueWork() {
        JobPublicationPaymentIntentCreateRecoveryService recoveryService =
                mock(JobPublicationPaymentIntentCreateRecoveryService.class);
        JobPublicationPaymentIntentCreateRecoveryScheduler scheduler =
                new JobPublicationPaymentIntentCreateRecoveryScheduler(recoveryService);
        when(recoveryService.findDueIds(25)).thenReturn(List.of(51L, 52L));
        org.mockito.Mockito.doThrow(new IllegalStateException("boom")).when(recoveryService).process(51L);

        scheduler.recoverCancelledOrphanPaymentIntents();

        verify(recoveryService).process(51L);
        verify(recoveryService).process(52L);
    }
}
