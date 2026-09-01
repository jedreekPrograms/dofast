package com.doFast.dofastapp.job.service;

import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.common.exception.BusinessException;
import com.doFast.dofastapp.job.assignment.JobAssignmentMode;
import com.doFast.dofastapp.job.category.FulfillmentMode;
import com.doFast.dofastapp.job.category.JobCategory;
import com.doFast.dofastapp.job.category.JobCategoryRepository;
import com.doFast.dofastapp.job.dto.JobRequest;
import com.doFast.dofastapp.job.dto.JobResponse;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.repository.JobRepository;
import com.doFast.dofastapp.job.search.alert.JobPublicationOutboxRepository;
import com.doFast.dofastapp.location.routing.dto.RoutePointRequest;
import com.doFast.dofastapp.location.routing.service.RouteQuoteService;
import com.doFast.dofastapp.location.service.GeoPointFactory;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OnSiteJobServiceTest {

    @Mock private JobRepository jobRepository;
    @Mock private JobCategoryRepository jobCategoryRepository;
    @Mock private TransactionService transactionService;
    @Mock private NotificationService notificationService;
    @Mock private RouteQuoteService routeQuoteService;
    @Mock private LiveTrackingService liveTrackingService;
    @Mock private JobPublicationOutboxRepository jobPublicationOutboxRepository;

    private JobService service;
    private User owner;
    private User worker;

    @BeforeEach
    void setUp() {
        service = new JobService(
                jobRepository,
                jobCategoryRepository,
                transactionService,
                notificationService,
                routeQuoteService,
                liveTrackingService,
                jobPublicationOutboxRepository
        );
        owner = user(1L, "owner@example.com");
        worker = user(2L, "worker@example.com");
    }

    @Test
    void createOnSiteJobStoresSinglePrivateLocationWithoutRouteQuote() {
        JobCategory category = leafCategory(70L, FulfillmentMode.ON_SITE);
        when(jobCategoryRepository.findByIdAndActiveTrue(70L)).thenReturn(Optional.of(category));
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JobRequest request = baseRequest(70L);
        request.setLocation(new RoutePointRequest(
                new BigDecimal("51.090100"),
                new BigDecimal("17.015200"),
                "Wrocław, Krzyki",
                "ul. Powstańców Śląskich 100, mieszkanie 8",
                "on-site-place"
        ));

        JobResponse response = service.createJob(request, owner);

        assertEquals(FulfillmentMode.ON_SITE, response.fulfillmentMode());
        assertEquals("Wrocław, Krzyki", response.locationLabel());
        assertNull(response.destinationLabel());
        assertNull(response.routeDistanceMeters());
        assertNull(response.routeDurationSeconds());
        verify(transactionService).holdMoney(any(Job.class));
        verifyNoInteractions(routeQuoteService);
    }

    @Test
    void createOnSiteJobRequiresSingleLocationAndRejectsRouteQuote() {
        JobCategory category = leafCategory(70L, FulfillmentMode.ON_SITE);
        when(jobCategoryRepository.findByIdAndActiveTrue(70L)).thenReturn(Optional.of(category));

        JobRequest missingLocation = baseRequest(70L);
        assertThrows(BusinessException.class, () -> service.createJob(missingLocation, owner));

        JobRequest mixedPayload = baseRequest(70L);
        mixedPayload.setRouteQuoteId(UUID.randomUUID());
        mixedPayload.setLocation(new RoutePointRequest(
                new BigDecimal("51.090100"),
                new BigDecimal("17.015200"),
                "Wrocław, Krzyki",
                "ul. Powstańców Śląskich 100",
                null
        ));
        assertThrows(BusinessException.class, () -> service.createJob(mixedPayload, owner));
    }

    @Test
    void acceptingOnSiteJobDoesNotStartCourierTracking() {
        JobCategory category = leafCategory(70L, FulfillmentMode.ON_SITE);
        Job job = new Job();
        ReflectionTestUtils.setField(job, "id", 10L);
        job.setTitle("Montaż półki");
        job.setDescription("Zamontuj półkę na ścianie w mieszkaniu.");
        job.setPrice(new BigDecimal("80.00"));
        job.setStatus(JobStatus.OPEN);
        job.setCategory(category);
        job.setLocation(GeoPointFactory.from(new BigDecimal("51.090100"), new BigDecimal("17.015200")));
        job.setLocationLabel("Wrocław, Krzyki");
        job.setLocationPrivateLabel("ul. Powstańców Śląskich 100, mieszkanie 8");
        job.setCreatedBy(owner);

        when(jobRepository.findByIdAndStatusAndAssignmentModeForUpdate(
                10L, JobStatus.OPEN, JobAssignmentMode.INSTANT
        )).thenReturn(Optional.of(job));
        when(jobRepository.save(job)).thenReturn(job);

        JobResponse response = service.acceptJob(10L, worker);

        assertEquals(JobStatus.IN_PROGRESS, response.status());
        assertEquals(worker.getId(), response.takenById());
        verify(jobRepository, never()).findByIdForUpdate(10L);
        verify(liveTrackingService, never()).initializeForAcceptedJob(any(Job.class));
    }

    private JobRequest baseRequest(Long categoryId) {
        JobRequest request = new JobRequest();
        request.setTitle("Montaż półki");
        request.setDescription("Zamontuj półkę na ścianie w mieszkaniu.");
        request.setPrice(new BigDecimal("80.00"));
        request.setCategoryId(categoryId);
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
        ReflectionTestUtils.setField(category, "slug", "montaz");
        ReflectionTestUtils.setField(category, "name", "Montaż");
        ReflectionTestUtils.setField(category, "fulfillmentMode", mode);
        ReflectionTestUtils.setField(category, "active", true);
        return category;
    }

    private User user(Long id, String email) {
        User user = new User(email, email);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
