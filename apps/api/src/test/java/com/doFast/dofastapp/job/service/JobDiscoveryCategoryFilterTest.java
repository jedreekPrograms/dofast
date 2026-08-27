package com.doFast.dofastapp.job.service;

import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.job.category.JobCategoryRepository;
import com.doFast.dofastapp.job.repository.JobRepository;
import com.doFast.dofastapp.job.search.alert.JobPublicationOutboxRepository;
import com.doFast.dofastapp.location.routing.service.RouteQuoteService;
import com.doFast.dofastapp.location.tracking.service.LiveTrackingService;
import com.doFast.dofastapp.notification.service.NotificationService;
import com.doFast.dofastapp.payment.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobDiscoveryCategoryFilterTest {

    @Mock private JobRepository jobRepository;
    @Mock private JobCategoryRepository jobCategoryRepository;
    @Mock private TransactionService transactionService;
    @Mock private NotificationService notificationService;
    @Mock private RouteQuoteService routeQuoteService;
    @Mock private LiveTrackingService liveTrackingService;
    @Mock private JobPublicationOutboxRepository jobPublicationOutboxRepository;

    private JobService jobService;

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
    }

    @Test
    void discoveryNormalizesAndAppliesCategorySlug() {
        when(jobRepository.findOpenJobsByCategory(
                eq(JobStatus.OPEN),
                eq("paczka"),
                eq("paczki-kurier"),
                eq(new BigDecimal("10.00")),
                eq(new BigDecimal("80.00")),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 12), 0));

        var response = jobService.getOpenJobs(
                "  paczka  ",
                "  PACZKI-KURIER  ",
                new BigDecimal("10.00"),
                new BigDecimal("80.00"),
                0,
                12
        );

        assertEquals(0, response.totalElements());
        verify(jobRepository).findOpenJobsByCategory(
                eq(JobStatus.OPEN),
                eq("paczka"),
                eq("paczki-kurier"),
                eq(new BigDecimal("10.00")),
                eq(new BigDecimal("80.00")),
                any(Pageable.class)
        );
    }

    @Test
    void blankCategoryKeepsLegacyDiscoveryPath() {
        when(jobRepository.findOpenJobs(eq(JobStatus.OPEN), eq(""), eq(null), eq(null), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 12), 0));

        var response = jobService.getOpenJobs(null, "   ", null, null, 0, 12);

        assertEquals(0, response.totalElements());
        verify(jobRepository).findOpenJobs(eq(JobStatus.OPEN), eq(""), eq(null), eq(null), any(Pageable.class));
    }
}
