package com.doFast.dofastapp.job.service;

import com.doFast.dofastapp.common.dto.PageResponse;
import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.common.exception.BusinessException;
import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.job.dto.JobRequest;
import com.doFast.dofastapp.job.dto.JobResponse;
import com.doFast.dofastapp.job.dto.JobRouteResponse;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.repository.JobRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock private JobRepository jobRepository;
    @Mock private TransactionService transactionService;
    @Mock private NotificationService notificationService;
    @Mock private RouteQuoteService routeQuoteService;
    @Mock private LiveTrackingService liveTrackingService;

    private JobService jobService;
    private User owner;
    private User worker;

    @BeforeEach
    void setUp() {
        jobService = new JobService(jobRepository, transactionService, notificationService, routeQuoteService, liveTrackingService);
        owner = user(1L, "owner@example.com");
        worker = user(2L, "worker@example.com");
    }

    @Test
    void createJobUsesServerRouteQuoteAndLocksFunds() {
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));
        UUID quoteId = UUID.randomUUID();
        when(routeQuoteService.consume(quoteId, owner)).thenReturn(routeQuote(quoteId, owner));

        JobRequest request = request(quoteId);
        JobResponse response = jobService.createJob(request, owner);

        assertEquals(JobStatus.OPEN, response.status());
        assertEquals(owner.getId(), response.createdById());
        assertEquals("Wrocław, Plac Grunwaldzki", response.locationLabel());
        assertEquals("Wrocław, Rynek", response.destinationLabel());
        assertEquals(4200, response.routeDistanceMeters());
        assertEquals(720, response.routeDurationSeconds());
        verify(transactionService).holdMoney(any(Job.class));
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
        when(jobRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(job));
        assertThrows(ForbiddenOperationException.class, () -> jobService.acceptJob(10L, owner));
    }

    @Test
    void unavailableJobCannotBeAcceptedAgain() {
        Job job = job(JobStatus.IN_PROGRESS, owner, worker);
        when(jobRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(job));
        assertThrows(ConflictException.class, () -> jobService.acceptJob(10L, user(3L, "other@example.com")));
    }

    @Test
    void workerRequestsCompletionBeforeOwnerCanConfirm() {
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Job job = job(JobStatus.IN_PROGRESS, owner, worker);
        when(jobRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(job));
        assertEquals(JobStatus.COMPLETION_REQUESTED, jobService.requestCompletion(10L, worker).status());
    }

    @Test
    void ownerConfirmingCompletionReleasesEscrowAndStopsTracking() {
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Job job = job(JobStatus.COMPLETION_REQUESTED, owner, worker);
        when(jobRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(job));
        assertEquals(JobStatus.DONE, jobService.confirmCompletion(10L, owner).status());
        verify(transactionService).releaseMoney(job, worker);
        verify(liveTrackingService).stopAndClear(10L);
    }

    @Test
    void acceptedJobCannotBeCancelledDirectly() {
        Job job = job(JobStatus.IN_PROGRESS, owner, worker);
        when(jobRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(job));
        assertThrows(ConflictException.class, () -> jobService.cancelJob(10L, owner));
    }

    @Test
    void assignedWorkerCanReadExactOriginAndFullRouteWhileJobIsActive() {
        Job job = job(JobStatus.IN_PROGRESS, owner, worker);
        when(jobRepository.findById(10L)).thenReturn(Optional.of(job));

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
    void unrelatedUserCannotReadExactRoute() {
        Job job = job(JobStatus.IN_PROGRESS, owner, worker);
        when(jobRepository.findById(10L)).thenReturn(Optional.of(job));
        assertThrows(ForbiddenOperationException.class, () -> jobService.getExactRoute(10L, user(3L, "stranger@example.com")));
    }

    @Test
    void workerCannotReadExactRouteAfterCompletion() {
        Job job = job(JobStatus.DONE, owner, worker);
        when(jobRepository.findById(10L)).thenReturn(Optional.of(job));
        assertThrows(ForbiddenOperationException.class, () -> jobService.getExactRoute(10L, worker));
    }

    private JobRequest request(UUID quoteId) {
        JobRequest request = new JobRequest();
        request.setTitle("Odbierz i dowieź paczkę");
        request.setDescription("Odbierz paczkę z punktu A i dostarcz ją do punktu B.");
        request.setPrice(new BigDecimal("25.00"));
        request.setRouteQuoteId(quoteId);
        return request;
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
