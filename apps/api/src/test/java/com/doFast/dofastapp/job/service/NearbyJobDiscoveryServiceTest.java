package com.doFast.dofastapp.job.service;

import com.doFast.dofastapp.job.repository.JobRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NearbyJobDiscoveryServiceTest {

    @Mock private JobRepository jobRepository;

    @Test
    void normalizesAndAppliesCategorySlug() {
        NearbyJobDiscoveryService service = new NearbyJobDiscoveryService(jobRepository);
        when(jobRepository.findNearbyOpenJobsByCategory(51.1, 17.03, 5000, "paczki-kurier", 25))
                .thenReturn(List.of());

        var response = service.getNearbyJobs(51.1, 17.03, 5000, "  PACZKI-KURIER  ", 25);

        assertTrue(response.isEmpty());
        verify(jobRepository).findNearbyOpenJobsByCategory(51.1, 17.03, 5000, "paczki-kurier", 25);
        verifyNoMoreInteractions(jobRepository);
    }

    @Test
    void blankCategoryKeepsLegacyGeospatialQuery() {
        NearbyJobDiscoveryService service = new NearbyJobDiscoveryService(jobRepository);
        when(jobRepository.findNearbyOpenJobs(51.1, 17.03, 5000, 25)).thenReturn(List.of());

        var response = service.getNearbyJobs(51.1, 17.03, 5000, "   ", 25);

        assertTrue(response.isEmpty());
        verify(jobRepository).findNearbyOpenJobs(51.1, 17.03, 5000, 25);
        verifyNoMoreInteractions(jobRepository);
    }

    @Test
    void nullCategoryKeepsLegacyGeospatialQuery() {
        NearbyJobDiscoveryService service = new NearbyJobDiscoveryService(jobRepository);
        when(jobRepository.findNearbyOpenJobs(51.1, 17.03, 5000, 25)).thenReturn(List.of());

        var response = service.getNearbyJobs(51.1, 17.03, 5000, null, 25);

        assertTrue(response.isEmpty());
        verify(jobRepository).findNearbyOpenJobs(51.1, 17.03, 5000, 25);
        verifyNoMoreInteractions(jobRepository);
    }
}
