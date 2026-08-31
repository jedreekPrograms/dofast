package com.doFast.dofastapp.location.retention;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExactLocationRetentionServiceTest {

    @Mock
    private ExactLocationRetentionRepository repository;

    @Test
    void cleanupUsesConfiguredUtcRetentionCutoffAndBatchSize() {
        ExactLocationRetentionProperties properties = new ExactLocationRetentionProperties(30, 125);
        Clock clock = Clock.fixed(Instant.parse("2026-08-31T12:00:00Z"), ZoneOffset.UTC);
        ExactLocationRetentionService service = new ExactLocationRetentionService(repository, properties, clock);
        LocalDateTime now = LocalDateTime.of(2026, 8, 31, 12, 0);
        LocalDateTime cutoff = LocalDateTime.of(2026, 8, 1, 12, 0);

        when(repository.purgeDueJobs(cutoff, 125)).thenReturn(3);
        when(repository.deleteExpiredUnreferencedRouteQuotes(now, 125)).thenReturn(4);

        ExactLocationRetentionService.CleanupResult result = service.cleanup();

        assertEquals(3, result.purgedJobs());
        assertEquals(4, result.deletedRouteQuotes());
        verify(repository).purgeDueJobs(cutoff, 125);
        verify(repository).deleteExpiredUnreferencedRouteQuotes(now, 125);
    }

    @Test
    void rejectsRetentionOutsideSupportedRange() {
        assertThrows(IllegalArgumentException.class, () -> new ExactLocationRetentionProperties(0, 100));
        assertThrows(IllegalArgumentException.class, () -> new ExactLocationRetentionProperties(3651, 100));
    }

    @Test
    void rejectsCleanupBatchOutsideSupportedRange() {
        assertThrows(IllegalArgumentException.class, () -> new ExactLocationRetentionProperties(30, 0));
        assertThrows(IllegalArgumentException.class, () -> new ExactLocationRetentionProperties(30, 1001));
    }
}
