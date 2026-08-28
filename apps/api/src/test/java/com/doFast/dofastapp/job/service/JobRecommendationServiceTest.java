package com.doFast.dofastapp.job.service;

import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.job.repository.JobDiscoveryRepository;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.repository.UserServiceCategoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobRecommendationServiceTest {

    @Mock private JobDiscoveryRepository jobDiscoveryRepository;
    @Mock private UserServiceCategoryRepository userServiceCategoryRepository;
    @Mock private User viewer;

    @Test
    void returnsEmptyWithoutQueryingJobsWhenUserHasNoActiveSpecializations() {
        when(viewer.getId()).thenReturn(42L);
        when(userServiceCategoryRepository.findActiveCategoryIdsForUser(42L)).thenReturn(List.of());

        JobRecommendationService service = new JobRecommendationService(
                jobDiscoveryRepository,
                userServiceCategoryRepository
        );

        var response = service.getRecommendedJobs(viewer, 0, 6);

        assertEquals(0, response.specializationCount());
        assertTrue(response.jobs().content().isEmpty());
        assertEquals(0, response.jobs().totalElements());
        verify(userServiceCategoryRepository).findActiveCategoryIdsForUser(42L);
        verifyNoInteractions(jobDiscoveryRepository);
    }

    @Test
    void queriesOpenJobsForViewerActiveSpecializations() {
        PageRequest pageable = PageRequest.of(
                1,
                8,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
        List<Long> categoryIds = List.of(3L, 5L);
        when(viewer.getId()).thenReturn(77L);
        when(userServiceCategoryRepository.findActiveCategoryIdsForUser(77L)).thenReturn(categoryIds);
        when(jobDiscoveryRepository.findRecommendedOpenJobs(
                JobStatus.OPEN,
                categoryIds,
                77L,
                pageable
        )).thenReturn(Page.empty(pageable));

        JobRecommendationService service = new JobRecommendationService(
                jobDiscoveryRepository,
                userServiceCategoryRepository
        );

        var response = service.getRecommendedJobs(viewer, 1, 8);

        assertEquals(2, response.specializationCount());
        assertTrue(response.jobs().content().isEmpty());
        assertEquals(1, response.jobs().page());
        verify(jobDiscoveryRepository).findRecommendedOpenJobs(
                JobStatus.OPEN,
                categoryIds,
                77L,
                pageable
        );
    }
}
