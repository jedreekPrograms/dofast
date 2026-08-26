package com.doFast.dofastapp.job.service;

import com.doFast.dofastapp.job.category.JobCategoryRepository;
import com.doFast.dofastapp.job.repository.JobRepository;
import com.doFast.dofastapp.location.routing.service.RouteQuoteService;
import com.doFast.dofastapp.location.tracking.service.LiveTrackingService;
import com.doFast.dofastapp.notification.service.NotificationService;
import com.doFast.dofastapp.payment.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
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
class NearbyJobDiscoveryCategoryFilterTest {

    @Mock private JobRepository jobRepository;
    @Mock private JobCategoryRepository jobCategoryRepository;
    @Mock private TransactionService transactionService;
    @Mock private NotificationService notificationService;
    @Mock private RouteQuoteService routeQuoteService;
    @Mock private LiveTrackingService liveTrackingService;

    private JobService jobService;

    @BeforeEach
    void setUp() {
        jobService = new JobService(
                jobRepository,
                jobCategoryRepository,
                transactionService,
                notificationService,
                routeQuoteService,
                liveTrackingService
        );
    }

    @Test
    void nearbyDiscoveryNormalizesAndAppliesCategorySlug() {
        when(jobRepository.findNearbyOpenJobsByCategory(51.1, 17.03, 5000, "paczki-kurier", 25))
                .thenReturn(List.of());

        var response = jobService.getNearbyJobs(51.1, 17.03, 5000, "  PACZKI-KURIER  ", 25);

        assertTrue(response.isEmpty());
        verify(jobRepository).findNearbyOpenJobsByCategory(51.1, 17.03, 5000, "paczki-kurier", 25);
        verifyNoMoreInteractions(jobRepository);
    }

    @Test
    void blankNearbyCategoryKeepsLegacyGeospatialQuery() {
        when(jobRepository.findNearbyOpenJobs(51.1, 17.03, 5000, 25)).thenReturn(List.of());

        var response = jobService.getNearbyJobs(51.1, 17.03, 5000, "   ", 25);

        assertTrue(response.isEmpty());
        verify(jobRepository).findNearbyOpenJobs(51.1, 17.03, 5000, 25);
        verifyNoMoreInteractions(jobRepository);
    }
}
