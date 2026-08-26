package com.doFast.dofastapp.job.service;

import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.job.category.FulfillmentMode;
import com.doFast.dofastapp.job.category.JobCategory;
import com.doFast.dofastapp.job.category.JobCategoryRepository;
import com.doFast.dofastapp.job.dto.JobRequest;
import com.doFast.dofastapp.job.dto.JobRouteResponse;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.repository.JobRepository;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobMultiStopRouteTest {

    @Mock private JobRepository jobRepository;
    @Mock private JobCategoryRepository categoryRepository;
    @Mock private TransactionService transactionService;
    @Mock private NotificationService notificationService;
    @Mock private RouteQuoteService routeQuoteService;
    @Mock private LiveTrackingService liveTrackingService;

    private JobService service;
    private User owner;
    private User worker;

    @BeforeEach
    void setUp() {
        service = new JobService(
                jobRepository,
                categoryRepository,
                transactionService,
                notificationService,
                routeQuoteService,
                liveTrackingService
        );
        owner = user(1L, "owner@example.com");
        worker = user(2L, "worker@example.com");
    }

    @Test
    void jobSnapshotsOrderedStopsAndExposesThemOnlyThroughExactRoute() {
        UUID quoteId = UUID.randomUUID();
        RouteQuote quote = routeQuote(quoteId);
        when(routeQuoteService.consume(quoteId, owner)).thenReturn(quote);
        when(categoryRepository.findByIdAndActiveTrue(42L)).thenReturn(Optional.of(leafCategory()));
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JobRequest request = new JobRequest();
        request.setTitle("Trasa z dwoma odbiorami");
        request.setDescription("Odbierz rzeczy w dwóch punktach po drodze i dostarcz do B.");
        request.setPrice(new BigDecimal("40.00"));
        request.setCategoryId(42L);
        request.setRouteQuoteId(quoteId);

        service.createJob(request, owner);

        ArgumentCaptor<Job> captor = ArgumentCaptor.forClass(Job.class);
        verify(jobRepository).save(captor.capture());
        Job job = captor.getValue();
        assertEquals(2, job.getRouteStops().size());
        assertEquals("Pierwszy dokładny adres", job.getRouteStops().get(0).getPrivateLabel());
        assertEquals("Drugi dokładny adres", job.getRouteStops().get(1).getPrivateLabel());

        ReflectionTestUtils.setField(job, "id", 99L);
        job.setStatus(JobStatus.IN_PROGRESS);
        job.setTakenBy(worker);
        when(jobRepository.findById(99L)).thenReturn(Optional.of(job));

        JobRouteResponse route = service.getExactRoute(99L, worker);
        assertEquals(2, route.stops().size());
        assertEquals("Pierwszy dokładny adres", route.stops().get(0).label());
        assertEquals("Drugi dokładny adres", route.stops().get(1).label());
    }

    private RouteQuote routeQuote(UUID id) {
        LocalDateTime now = LocalDateTime.now();
        RouteQuote quote = new RouteQuote();
        quote.initialize(
                id,
                owner,
                GeoPointFactory.from(new BigDecimal("51.1128"), new BigDecimal("17.0601")),
                "Wrocław, Start",
                "Start dokładny",
                "start-place",
                GeoPointFactory.from(new BigDecimal("51.1090"), new BigDecimal("17.0320")),
                "Wrocław, Meta",
                "Meta dokładna",
                "end-place",
                new RouteProviderResult(6_000, 1_000, "encoded", "GOOGLE_ROUTES"),
                now,
                now.plusMinutes(15)
        );
        quote.addStop(
                GeoPointFactory.from(new BigDecimal("51.1115"), new BigDecimal("17.0520")),
                "Wrocław, Stop 1",
                "Pierwszy dokładny adres",
                "stop-1"
        );
        quote.addStop(
                GeoPointFactory.from(new BigDecimal("51.1102"), new BigDecimal("17.0420")),
                "Wrocław, Stop 2",
                "Drugi dokładny adres",
                "stop-2"
        );
        return quote;
    }

    private JobCategory leafCategory() {
        JobCategory parent = new JobCategory();
        ReflectionTestUtils.setField(parent, "id", 100L);
        ReflectionTestUtils.setField(parent, "active", true);
        JobCategory category = new JobCategory();
        ReflectionTestUtils.setField(category, "id", 42L);
        ReflectionTestUtils.setField(category, "parent", parent);
        ReflectionTestUtils.setField(category, "slug", "mala-paczka");
        ReflectionTestUtils.setField(category, "name", "Mała paczka");
        ReflectionTestUtils.setField(category, "fulfillmentMode", FulfillmentMode.POINT_TO_POINT);
        ReflectionTestUtils.setField(category, "active", true);
        return category;
    }

    private User user(Long id, String email) {
        User user = new User(email, email);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
