package com.doFast.dofastapp.job.service;

import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.common.exception.BusinessException;
import com.doFast.dofastapp.job.category.FulfillmentMode;
import com.doFast.dofastapp.job.category.JobCategory;
import com.doFast.dofastapp.job.category.JobCategoryRepository;
import com.doFast.dofastapp.job.dto.JobRequest;
import com.doFast.dofastapp.job.dto.JobResponse;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.repository.JobRepository;
import com.doFast.dofastapp.location.routing.dto.RoutePointRequest;
import com.doFast.dofastapp.location.routing.service.RouteQuoteService;
import com.doFast.dofastapp.location.tracking.service.LiveTrackingService;
import com.doFast.dofastapp.notification.service.NotificationService;
import com.doFast.dofastapp.payment.service.TransactionService;
import com.doFast.dofastapp.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobOnSiteCreationTest {

    @Mock private JobRepository jobRepository;
    @Mock private JobCategoryRepository jobCategoryRepository;
    @Mock private TransactionService transactionService;
    @Mock private NotificationService notificationService;
    @Mock private RouteQuoteService routeQuoteService;
    @Mock private LiveTrackingService liveTrackingService;

    private JobService jobService;
    private User owner;

    @BeforeEach
    void setUp() {
        jobService = new JobService(jobRepository, jobCategoryRepository, transactionService,
                notificationService, routeQuoteService, liveTrackingService);
        owner = new User("owner@example.com", "owner@example.com");
        ReflectionTestUtils.setField(owner, "id", 1L);
    }

    @Test
    void onSiteJobUsesSinglePrivateLocationWithoutConsumingRouteQuote() {
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobCategoryRepository.findByIdAndActiveTrue(55L)).thenReturn(Optional.of(leafCategory(55L, FulfillmentMode.ON_SITE)));

        JobRequest request = baseRequest();
        request.setCategoryId(55L);
        request.setLocation(new RoutePointRequest(
                new BigDecimal("51.1128"), new BigDecimal("17.0601"),
                "Wrocław, Plac Grunwaldzki", "ul. Grunwaldzka 10, mieszkanie 4", "place-1"
        ));

        JobResponse response = jobService.createJob(request, owner);

        assertEquals(JobStatus.OPEN, response.status());
        assertEquals(FulfillmentMode.ON_SITE, response.fulfillmentMode());
        assertEquals("Wrocław, Plac Grunwaldzki", response.locationLabel());
        assertNull(response.destinationLabel());
        assertNull(response.routeDistanceMeters());
        assertNull(response.routeDurationSeconds());
        verify(routeQuoteService, never()).consume(any(UUID.class), any(User.class));
        verify(transactionService).holdMoney(any(Job.class));
    }

    @Test
    void onSiteJobRejectsRouteQuoteInsteadOfSingleLocation() {
        when(jobCategoryRepository.findByIdAndActiveTrue(55L)).thenReturn(Optional.of(leafCategory(55L, FulfillmentMode.ON_SITE)));
        JobRequest request = baseRequest();
        request.setCategoryId(55L);
        request.setRouteQuoteId(UUID.randomUUID());

        assertThrows(BusinessException.class, () -> jobService.createJob(request, owner));
    }

    @Test
    void pointToPointJobRejectsDirectLocationPayload() {
        when(jobCategoryRepository.findByIdAndActiveTrue(42L)).thenReturn(Optional.of(leafCategory(42L, FulfillmentMode.POINT_TO_POINT)));
        JobRequest request = baseRequest();
        request.setCategoryId(42L);
        request.setLocation(new RoutePointRequest(
                new BigDecimal("51.1128"), new BigDecimal("17.0601"),
                "Wrocław", "Dokładny adres", null
        ));

        assertThrows(BusinessException.class, () -> jobService.createJob(request, owner));
    }

    private JobRequest baseRequest() {
        JobRequest request = new JobRequest();
        request.setTitle("Montaż półki");
        request.setDescription("Potrzebuję pomocy z montażem półki na ścianie.");
        request.setPrice(new BigDecimal("80.00"));
        return request;
    }

    private JobCategory leafCategory(Long id, FulfillmentMode mode) {
        JobCategory parent = new JobCategory();
        ReflectionTestUtils.setField(parent, "id", id + 1000);
        ReflectionTestUtils.setField(parent, "slug", "parent");
        ReflectionTestUtils.setField(parent, "name", "Parent");
        ReflectionTestUtils.setField(parent, "active", true);

        JobCategory category = new JobCategory();
        ReflectionTestUtils.setField(category, "id", id);
        ReflectionTestUtils.setField(category, "parent", parent);
        ReflectionTestUtils.setField(category, "slug", "leaf-" + id);
        ReflectionTestUtils.setField(category, "name", "Leaf");
        ReflectionTestUtils.setField(category, "fulfillmentMode", mode);
        ReflectionTestUtils.setField(category, "active", true);
        return category;
    }
}
