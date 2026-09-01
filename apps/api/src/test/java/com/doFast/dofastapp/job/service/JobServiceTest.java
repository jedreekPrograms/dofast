package com.doFast.dofastapp.job.service;

import com.doFast.dofastapp.common.dto.PageResponse;
import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.common.exception.BusinessException;
import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
import com.doFast.dofastapp.job.category.FulfillmentMode;
import com.doFast.dofastapp.job.category.JobCategory;
import com.doFast.dofastapp.job.category.JobCategoryRepository;
import com.doFast.dofastapp.job.dto.JobRequest;
import com.doFast.dofastapp.job.dto.JobResponse;
import com.doFast.dofastapp.job.dto.JobRouteResponse;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.repository.JobRepository;
import com.doFast.dofastapp.job.search.alert.JobPublicationOutbox;
import com.doFast.dofastapp.job.search.alert.JobPublicationOutboxRepository;
import com.doFast.dofastapp.location.dto.LocationResponse;
import com.doFast.dofastapp.location.routing.entity.RouteQuote;
import com.doFast.dofastapp.location.routing.provider.RouteProviderResult;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock private JobRepository jobRepository;
    @Mock private JobCategoryRepository jobCategoryRepository;
    @Mock private TransactionService transactionService;
    @Mock private NotificationService notificationService;
    @Mock private RouteQuoteService routeQuoteService;
    @Mock private LiveTrackingService liveTrackingService;
    @Mock private JobPublicationOutboxRepository jobPublicationOutboxRepository;

    private JobService jobService;
    private User owner;
    private User worker;

    @BeforeEach
    void setUp() {
        jobService = new JobService(
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
    void createJobUsesServerRouteQuoteAssignsLeafCategoryLocksFundsAndEnqueuesPublication() {
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));
        UUID quoteId = UUID.randomUUID();
        when(routeQuoteService.consume(quoteId, owner)).thenReturn(routeQuote(quoteId, owner));
        JobCategory category = leafCategory(42L, "mala-paczka", "Mała paczka", FulfillmentMode.POINT_TO_POINT);
        when(jobCategoryRepository.findByIdAndActiveTrue(42L)).thenReturn(Optional.of(category));

        JobRequest request = request(quoteId);
        JobResponse response = jobService.createJob(request, owner);

        assertEquals(JobStatus.OPEN, response.status());
        assertEquals(owner.getId(), response.createdById());
        assertEquals(42L, response.categoryId());
        assertEquals("mala-paczka", response.categorySlug());
        assertEquals(FulfillmentMode.POINT_TO_POINT, response.fulfillmentMode());
        assertEquals("Wrocław, Plac Grunwaldzki", response.locationLabel());
        assertEquals("Wrocław, Rynek", response.destinationLabel());
        assertEquals(4200, response.routeDistanceMeters());
        assertEquals(720, response.routeDurationSeconds());
        verify(transactionService).holdMoney(any(Job.class));
        verify(jobPublicationOutboxRepository).save(any(JobPublicationOutbox.class));
    }

    @Test
    void createJobRejectsParentCategory() {
        JobCategory parent = new JobCategory();
        ReflectionTestUtils.setField(parent, "id", 10L);
        ReflectionTestUtils.setField(parent, "slug", "paczki-kurier");
        ReflectionTestUtils.setField(parent, "name", "Paczki i kurier");
        ReflectionTestUtils.setField(parent, "active", true);
        when(jobCategoryRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(parent));
        JobRequest request = request(UUID.randomUUID());
        request.setCategoryId(10L);

        assertThrows(BusinessException.class, () -> jobService.createJob(request, owner));
    }

    @Test
    void discoveryReturnsStablePaginationMetadata() {
        Job job = job(JobStatus.OPEN, owner, null);
        when(jobRepository.findOpenJobs(eq(JobStatus.OPEN), eq("zakupy"), eq(new BigDecimal("10.00")),
                eq(new BigDecimal("50.00")), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(job), PageRequest.of(0, 20), 1));

        PageResponse<JobResponse> response = jobService.getOpenJobs("  zakupy  ", new BigDecimal("10.00"),
                new BigDecimal("50.00"), 0, 20);

        assertEquals(1, response.content().size());
        assertEquals(0, response.page());
        assertEquals(20, response.size());
        assertEquals("Wrocław, Plac Grunwaldzki", response.content().getFirst().locationLabel());
        assertEquals("Wrocław, Rynek", response.content().getFirst().destinationLabel());
    }

    @Test
    void discoveryUsesTypedEmptyStringWhenTextQueryIsMissing() {
        when(jobRepository.findOpenJobs(eq(JobStatus.OPEN), eq(""), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        PageResponse<JobResponse> response = jobService.getOpenJobs(null, null, null, 0, 20);
        assertEquals(0, response.totalElements());
        verify(jobRepository).findOpenJobs(eq(JobStatus.OPEN), eq(""), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    void discoveryUsesTypedEmptyStringWhenTextQueryIsBlank() {
        when(jobRepository.findOpenJobs(eq(JobStatus.OPEN), eq(""), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        jobService.getOpenJobs("   ", null, null, 0, 20);
        verify(jobRepository).findOpenJobs(eq(JobStatus.OPEN), eq(""), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    void discoveryRejectsInvertedPriceRange() {
        assertThrows(BusinessException.class, () -> jobService.getOpenJobs(null,
                new BigDecimal("50.00"), new BigDecimal("10.00"), 0, 20));
    }

    @Test
    void ownerCannotAcceptOwnJob() {
        Job job = job(JobStatus.OPEN, owner, null);
        when(jobRepository.findPublicOrParticipantByIdForUpdate(10L, JobStatus.OPEN, owner.getId()))
                .thenReturn(Optional.of(job));
        assertThrows(ForbiddenOperationException.class, () -> jobService.acceptJob(10L, owner));
    }

    @Test
    void participantGetsLifecycleConflictWhenJobWasAlreadyAccepted() {
        Job job = job(JobStatus.IN_PROGRESS, owner, worker);
        when(jobRepository.findPublicOrParticipantByIdForUpdate(10L, JobStatus.OPEN, worker.getId()))
                .thenReturn(Optional.of(job));
        assertThrows(ConflictException.class, () -> jobService.acceptJob(10L, worker));
    }

    @Test
    void outsiderCannotEnumerateUnavailableJobThroughAccept() {
        User outsider = user(3L, "other@example.com");
        when(jobRepository.findPublicOrParticipantByIdForUpdate(10L, JobStatus.OPEN, outsider.getId()))
                .thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> jobService.acceptJob(10L, outsider));
        verify(jobRepository, never()).findByIdForUpdate(10L);
    }

    @Test
    void workerRequestsCompletionBeforeOwnerCanConfirm() {
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Job job = job(JobStatus.IN_PROGRESS, owner, worker);
        when(jobRepository.findParticipantByIdForUpdate(10L, worker.getId())).thenReturn(Optional.of(job));
        assertEquals(JobStatus.COMPLETION_REQUESTED, jobService.requestCompletion(10L, worker).status());
    }

    @Test
    void outsiderCannotEnumerateJobThroughCompletionRequest() {
        User outsider = user(3L, "stranger@example.com");
        when(jobRepository.findParticipantByIdForUpdate(10L, outsider.getId())).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> jobService.requestCompletion(10L, outsider));
        verify(jobRepository, never()).findByIdForUpdate(10L);
        verify(jobRepository, never()).save(any());
    }

    @Test
    void ownerConfirmingCompletionReleasesEscrowAndStopsTracking() {
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Job job = job(JobStatus.COMPLETION_REQUESTED, owner, worker);
        when(jobRepository.findParticipantByIdForUpdate(10L, owner.getId())).thenReturn(Optional.of(job));
        assertEquals(JobStatus.DONE, jobService.confirmCompletion(10L, owner).status());
        verify(transactionService).releaseMoney(job, worker);
        verify(liveTrackingService).stopAndClear(10L);
    }

    @Test
    void outsiderCannotEnumerateJobThroughCompletionConfirmation() {
        User outsider = user(3L, "stranger@example.com");
        when(jobRepository.findParticipantByIdForUpdate(10L, outsider.getId())).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> jobService.confirmCompletion(10L, outsider));
        verify(transactionService, never()).releaseMoney(any(), any());
    }

    @Test
    void acceptedJobCannotBeCancelledDirectly() {
        Job job = job(JobStatus.IN_PROGRESS, owner, worker);
        when(jobRepository.findParticipantByIdForUpdate(10L, owner.getId())).thenReturn(Optional.of(job));
        assertThrows(ConflictException.class, () -> jobService.cancelJob(10L, owner));
    }

    @Test
    void outsiderCannotEnumerateJobThroughDirectCancellation() {
        User outsider = user(3L, "stranger@example.com");
        when(jobRepository.findParticipantByIdForUpdate(10L, outsider.getId())).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> jobService.cancelJob(10L, outsider));
        verify(transactionService, never()).refundMoney(any());
    }

    @Test
    void assignedWorkerCanReadExactOriginAndFullRouteWhileJobIsActive() {
        Job job = job(JobStatus.IN_PROGRESS, owner, worker);
        when(jobRepository.findParticipantById(10L, worker.getId())).thenReturn(Optional.of(job));

        LocationResponse origin = jobService.getExactLocation(10L, worker);
        JobRouteResponse route = jobService.getExactRoute(10L, worker);

        assertEquals(51.1128, origin.latitude(), 0.000001);
        assertEquals("ul. Grunwaldzka 10, wejście A", origin.label());
        assertEquals(51.1090, route.destination().latitude(), 0.000001);
        assertEquals(17.0320, route.destination().longitude(), 0.000001);
        assertEquals("Rynek 1, wejście od placu", route.destination().label());
        assertEquals(4200, route.distanceMeters());
        assertEquals(720, route.durationSeconds());
    }

    @Test
    void unrelatedUserCannotEnumerateJobThroughExactRoute() {
        User outsider = user(3L, "stranger@example.com");
        when(jobRepository.findParticipantById(10L, outsider.getId())).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> jobService.getExactRoute(10L, outsider));
        verify(jobRepository, never()).findById(10L);
    }

    @Test
    void workerCannotReadExactRouteAfterCompletion() {
        Job job = job(JobStatus.DONE, owner, worker);
        when(jobRepository.findParticipantById(10L, worker.getId())).thenReturn(Optional.of(job));
        assertThrows(ForbiddenOperationException.class, () -> jobService.getExactRoute(10L, worker));
    }

    private JobRequest request(UUID quoteId) {
        JobRequest request = new JobRequest();
        request.setTitle("Odbierz i dowieź paczkę");
        request.setDescription("Odbierz paczkę z punktu A i dostarcz ją do punktu B.");
        request.setPrice(new BigDecimal("25.00"));
        request.setCategoryId(42L);
        request.setRouteQuoteId(quoteId);
        return request;
    }

    private JobCategory leafCategory(Long id, String slug, String name, FulfillmentMode mode) {
        JobCategory parent = new JobCategory();
        ReflectionTestUtils.setField(parent, "id", id + 1000);
        ReflectionTestUtils.setField(parent, "slug", "parent");
        ReflectionTestUtils.setField(parent, "name", "Parent");
        ReflectionTestUtils.setField(parent, "active", true);
        JobCategory category = new JobCategory();
        ReflectionTestUtils.setField(category, "id", id);
        ReflectionTestUtils.setField(category, "parent", parent);
        ReflectionTestUtils.setField(category, "slug", slug);
        ReflectionTestUtils.setField(category, "name", name);
        ReflectionTestUtils.setField(category, "fulfillmentMode", mode);
        ReflectionTestUtils.setField(category, "active", true);
        return category;
    }

    private RouteQuote routeQuote(UUID id, User user) {
        RouteQuote quote = new RouteQuote();
        LocalDateTime now = LocalDateTime.now();
        quote.initialize(
                id, user,
                GeoPointFactory.from(new BigDecimal("51.1128"), new BigDecimal("17.0601")),
                "Wrocław, Plac Grunwaldzki", "ul. Grunwaldzka 10, wejście A", "origin-place",
                GeoPointFactory.from(new BigDecimal("51.1090"), new BigDecimal("17.0320")),
                "Wrocław, Rynek", "Rynek 1, wejście od placu", "destination-place",
                new RouteProviderResult(4200, 720, "encoded", "GOOGLE_ROUTES"), now, now.plusMinutes(15)
        );
        return quote;
    }

    private Job job(JobStatus status, User createdBy, User takenBy) {
        Job job = new Job();
        ReflectionTestUtils.setField(job, "id", 10L);
        job.setTitle("Test job");
        job.setDescription("Test job description");
        job.setPrice(new BigDecimal("20.00"));
        job.setStatus(status);
        job.setLocation(GeoPointFactory.from(new BigDecimal("51.1128"), new BigDecimal("17.0601")));
        job.setLocationLabel("Wrocław, Plac Grunwaldzki");
        job.setLocationPrivateLabel("ul. Grunwaldzka 10, wejście A");
        job.setDestinationLocation(GeoPointFactory.from(new BigDecimal("51.1090"), new BigDecimal("17.0320")));
        job.setDestinationLabel("Wrocław, Rynek");
        job.setDestinationPrivateLabel("Rynek 1, wejście od placu");
        job.setRouteDistanceMeters(4200);
        job.setRouteDurationSeconds(720);
        job.setRouteEncodedPolyline("encoded");
        job.setRouteProvider("GOOGLE_ROUTES");
        job.setRouteComputedAt(LocalDateTime.now());
        job.setCreatedBy(createdBy);
        job.setTakenBy(takenBy);
        return job;
    }

    private User user(Long id, String email) {
        User user = new User(email, email);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
