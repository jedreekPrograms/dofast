package com.doFast.dofastapp.job.service;

import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.common.exception.BusinessException;
import com.doFast.dofastapp.job.repository.JobDiscoveryRepository;
import com.doFast.dofastapp.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobDiscoveryServiceTest {

    @Mock private JobDiscoveryRepository jobDiscoveryRepository;
    @Mock private User viewer;

    @Test
    void authenticatedOpenDiscoveryPassesViewerIdToBlockAwareQuery() {
        JobDiscoveryService service = new JobDiscoveryService(jobDiscoveryRepository);
        PageRequest pageable = discoveryPage(1, 15);
        when(viewer.getId()).thenReturn(42L);
        when(jobDiscoveryRepository.findOpenJobs(
                JobStatus.OPEN,
                "paczka",
                new BigDecimal("20.00"),
                new BigDecimal("200.00"),
                42L,
                pageable
        )).thenReturn(Page.empty(pageable));

        var response = service.getOpenJobs(
                "  paczka  ",
                null,
                new BigDecimal("20.00"),
                new BigDecimal("200.00"),
                1,
                15,
                viewer
        );

        assertTrue(response.content().isEmpty());
        verify(jobDiscoveryRepository).findOpenJobs(
                JobStatus.OPEN,
                "paczka",
                new BigDecimal("20.00"),
                new BigDecimal("200.00"),
                42L,
                pageable
        );
        verifyNoMoreInteractions(jobDiscoveryRepository);
    }

    @Test
    void anonymousDiscoveryKeepsPublicBehaviorWithNullViewerId() {
        JobDiscoveryService service = new JobDiscoveryService(jobDiscoveryRepository);
        PageRequest pageable = discoveryPage(0, 20);
        when(jobDiscoveryRepository.findOpenJobs(JobStatus.OPEN, "", null, null, null, pageable))
                .thenReturn(Page.empty(pageable));

        var response = service.getOpenJobs(null, null, null, null, 0, 20, null);

        assertTrue(response.content().isEmpty());
        verify(jobDiscoveryRepository).findOpenJobs(JobStatus.OPEN, "", null, null, null, pageable);
        verifyNoMoreInteractions(jobDiscoveryRepository);
    }

    @Test
    void categoryNearbyDiscoveryNormalizesSlugAndPassesViewerId() {
        JobDiscoveryService service = new JobDiscoveryService(jobDiscoveryRepository);
        when(viewer.getId()).thenReturn(77L);
        when(jobDiscoveryRepository.findNearbyOpenJobsByCategory(
                51.1,
                17.03,
                5000,
                "paczki-kurier",
                77L,
                25
        )).thenReturn(List.of());

        var response = service.getNearbyJobs(
                51.1,
                17.03,
                5000,
                "  PACZKI-KURIER  ",
                25,
                viewer
        );

        assertTrue(response.isEmpty());
        verify(jobDiscoveryRepository).findNearbyOpenJobsByCategory(
                51.1,
                17.03,
                5000,
                "paczki-kurier",
                77L,
                25
        );
        verifyNoMoreInteractions(jobDiscoveryRepository);
    }

    @Test
    void nearbyAnonymousDiscoveryUsesNullViewerId() {
        JobDiscoveryService service = new JobDiscoveryService(jobDiscoveryRepository);
        when(jobDiscoveryRepository.findNearbyOpenJobs(51.1, 17.03, 5000, null, 25))
                .thenReturn(List.of());

        var response = service.getNearbyJobs(51.1, 17.03, 5000, null, 25, null);

        assertTrue(response.isEmpty());
        verify(jobDiscoveryRepository).findNearbyOpenJobs(51.1, 17.03, 5000, null, 25);
        verifyNoMoreInteractions(jobDiscoveryRepository);
    }

    @Test
    void rejectsInvertedPriceRangeBeforeQueryingRepository() {
        JobDiscoveryService service = new JobDiscoveryService(jobDiscoveryRepository);

        assertThrows(BusinessException.class, () -> service.getOpenJobs(
                null,
                null,
                new BigDecimal("200.00"),
                new BigDecimal("20.00"),
                0,
                20,
                null
        ));

        verifyNoMoreInteractions(jobDiscoveryRepository);
    }

    private PageRequest discoveryPage(int page, int size) {
        return PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }
}
