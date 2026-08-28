package com.doFast.dofastapp.job.service;

import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.job.repository.JobDiscoveryRepository;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.entity.UserServiceArea;
import com.doFast.dofastapp.user.repository.UserServiceAreaRepository;
import com.doFast.dofastapp.user.repository.UserServiceCategoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobRecommendationServiceTest {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @Mock private JobDiscoveryRepository jobDiscoveryRepository;
    @Mock private UserServiceCategoryRepository userServiceCategoryRepository;
    @Mock private UserServiceAreaRepository userServiceAreaRepository;
    @Mock private User viewer;

    @Test
    void returnsEmptyWithoutQueryingJobsWhenUserHasNoActiveSpecializations() {
        when(viewer.getId()).thenReturn(42L);
        when(userServiceCategoryRepository.findActiveCategoryIdsForUser(42L)).thenReturn(List.of());
        when(userServiceAreaRepository.findByUser_Id(42L)).thenReturn(Optional.empty());

        JobRecommendationService service = service();
        var response = service.getRecommendedJobs(viewer, 0, 6);

        assertEquals(0, response.specializationCount());
        assertNull(response.serviceAreaRadiusKm());
        assertTrue(response.jobs().content().isEmpty());
        assertEquals(0, response.jobs().totalElements());
        verify(userServiceCategoryRepository).findActiveCategoryIdsForUser(42L);
        verify(userServiceAreaRepository).findByUser_Id(42L);
        verifyNoInteractions(jobDiscoveryRepository);
    }

    @Test
    void keepsCategoryOnlyRecommendationsWhenNoServiceAreaConfigured() {
        PageRequest pageable = PageRequest.of(1, 8);
        List<Long> categoryIds = List.of(3L, 5L);
        when(viewer.getId()).thenReturn(77L);
        when(userServiceCategoryRepository.findActiveCategoryIdsForUser(77L)).thenReturn(categoryIds);
        when(userServiceAreaRepository.findByUser_Id(77L)).thenReturn(Optional.empty());
        when(jobDiscoveryRepository.findRecommendedOpenJobs(
                JobStatus.OPEN,
                categoryIds,
                77L,
                pageable
        )).thenReturn(Page.empty(pageable));

        JobRecommendationService service = service();
        var response = service.getRecommendedJobs(viewer, 1, 8);

        assertEquals(2, response.specializationCount());
        assertNull(response.serviceAreaRadiusKm());
        assertTrue(response.jobs().content().isEmpty());
        assertEquals(1, response.jobs().page());
        verify(jobDiscoveryRepository).findRecommendedOpenJobs(
                JobStatus.OPEN,
                categoryIds,
                77L,
                pageable
        );
    }

    @Test
    void filtersRecommendationsByPrivateServiceAreaWhenConfigured() {
        PageRequest pageable = PageRequest.of(0, 6);
        List<Long> categoryIds = List.of(3L, 5L);
        UserServiceArea area = new UserServiceArea(viewer);
        area.setCenterLocation(GEOMETRY_FACTORY.createPoint(new Coordinate(17.0385, 51.1079)));
        area.setRadiusMeters(20_000);

        when(viewer.getId()).thenReturn(88L);
        when(userServiceCategoryRepository.findActiveCategoryIdsForUser(88L)).thenReturn(categoryIds);
        when(userServiceAreaRepository.findByUser_Id(88L)).thenReturn(Optional.of(area));
        when(jobDiscoveryRepository.findRecommendedOpenJobsInArea(
                categoryIds,
                88L,
                51.1079,
                17.0385,
                20_000,
                pageable
        )).thenReturn(Page.empty(pageable));

        JobRecommendationService service = service();
        var response = service.getRecommendedJobs(viewer, 0, 6);

        assertEquals(2, response.specializationCount());
        assertEquals(20, response.serviceAreaRadiusKm());
        assertTrue(response.jobs().content().isEmpty());
        verify(jobDiscoveryRepository).findRecommendedOpenJobsInArea(
                categoryIds,
                88L,
                51.1079,
                17.0385,
                20_000,
                pageable
        );
    }

    private JobRecommendationService service() {
        return new JobRecommendationService(
                jobDiscoveryRepository,
                userServiceCategoryRepository,
                userServiceAreaRepository
        );
    }
}
