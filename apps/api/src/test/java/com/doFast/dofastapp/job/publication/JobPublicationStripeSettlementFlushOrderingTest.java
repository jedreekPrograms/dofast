package com.doFast.dofastapp.job.publication;

import com.doFast.dofastapp.job.category.FulfillmentMode;
import com.doFast.dofastapp.job.category.JobCategory;
import com.doFast.dofastapp.job.category.JobCategoryRepository;
import com.doFast.dofastapp.job.dto.JobRequest;
import com.doFast.dofastapp.job.dto.JobResponse;
import com.doFast.dofastapp.job.service.JobService;
import com.doFast.dofastapp.location.routing.repository.RouteQuoteRepository;
import com.doFast.dofastapp.payment.service.StripePaymentService;
import com.doFast.dofastapp.user.entity.User;
import com.stripe.model.PaymentIntent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobPublicationStripeSettlementFlushOrderingTest {

    @Mock private JobPublicationRepository publicationRepository;
    @Mock private JobPublicationService publicationService;
    @Mock private StripePaymentService stripePaymentService;
    @Mock private JobCategoryRepository categoryRepository;
    @Mock private RouteQuoteRepository routeQuoteRepository;
    @Mock private JobService jobService;

    private JobPublicationStripeSettlementService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new JobPublicationStripeSettlementService(
                publicationRepository,
                publicationService,
                stripePaymentService,
                categoryRepository,
                routeQuoteRepository,
                jobService
        );
        user = new User("flush-owner@example.com", "flushOwner");
        ReflectionTestUtils.setField(user, "id", 7L);
    }

    @Test
    void publishPathKeepsDatabaseValidStateUntilFlushCapableWritesFinish() {
        JobPublication publication = pendingPublication(LocalDateTime.now().plusMinutes(5));
        JobRequest request = new JobRequest();
        JobResponse job = mock(JobResponse.class);
        when(job.id()).thenReturn(99L);
        when(publicationRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(publication));
        when(stripePaymentService.processSuccessfulJobPublicationPayment(any(), eq("evt_flush"), eq(11L)))
                .thenReturn(true);
        when(categoryRepository.findByIdAndActiveTrue(42L)).thenReturn(Optional.of(onSiteLeafCategory()));
        when(publicationService.deserialize("payload")).thenReturn(request);

        doAnswer(invocation -> {
            assertEquals(JobPublicationStatus.PAYMENT_REQUIRED, publication.getStatus());
            assertNull(publication.getPaymentReceivedAt());
            return null;
        }).when(publicationService).restoreReservation(publication);

        when(jobService.createJob(request, user)).thenAnswer(invocation -> {
            assertEquals(JobPublicationStatus.PAYMENT_REQUIRED, publication.getStatus());
            assertNull(publication.getPaymentReceivedAt());
            return job;
        });

        service.processSuccessfulPayment(paymentIntent(), "evt_flush");

        assertEquals(JobPublicationStatus.PUBLISHED, publication.getStatus());
        assertEquals(99L, publication.getPublishedJobId());
        assertNotNull(publication.getPaymentReceivedAt());
    }

    @Test
    void recoveryPathKeepsDatabaseValidStateUntilReservationRestoreFinishes() {
        JobPublication publication = pendingPublication(LocalDateTime.now().minusSeconds(1));
        when(publicationRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(publication));
        when(stripePaymentService.processSuccessfulJobPublicationPayment(any(), eq("evt_expired_flush"), eq(11L)))
                .thenReturn(true);

        doAnswer(invocation -> {
            assertEquals(JobPublicationStatus.PAYMENT_REQUIRED, publication.getStatus());
            assertNull(publication.getPaymentReceivedAt());
            return null;
        }).when(publicationService).restoreReservation(publication);

        service.processSuccessfulPayment(paymentIntent(), "evt_expired_flush");

        assertEquals(JobPublicationStatus.PAYMENT_RECEIVED, publication.getStatus());
        assertEquals(JobPublicationRecoveryReason.PUBLICATION_EXPIRED, publication.getRecoveryReason());
        assertNotNull(publication.getPaymentReceivedAt());
    }

    private JobPublication pendingPublication(LocalDateTime expiresAt) {
        JobPublication publication = new JobPublication();
        publication.initializePaymentRequired(
                user,
                "job-publication:7:req-flush",
                "hash",
                "payload",
                42L,
                null,
                new BigDecimal("70.00"),
                new BigDecimal("25.00"),
                new BigDecimal("45.00"),
                LocalDateTime.now().minusMinutes(1),
                expiresAt
        );
        ReflectionTestUtils.setField(publication, "id", 11L);
        publication.attachStripePaymentIntent("pi_flush", LocalDateTime.now());
        return publication;
    }

    private PaymentIntent paymentIntent() {
        PaymentIntent intent = new PaymentIntent();
        intent.setId("pi_flush");
        intent.setAmount(4500L);
        intent.setCurrency("pln");
        intent.setStatus("succeeded");
        intent.setMetadata(Map.of(
                "userId", "7",
                "purpose", JobPublicationPaymentIntentService.PURPOSE,
                "jobPublicationId", "11"
        ));
        return intent;
    }

    private JobCategory onSiteLeafCategory() {
        JobCategory parent = new JobCategory();
        ReflectionTestUtils.setField(parent, "id", 1L);
        JobCategory leaf = new JobCategory();
        ReflectionTestUtils.setField(leaf, "id", 42L);
        ReflectionTestUtils.setField(leaf, "parent", parent);
        ReflectionTestUtils.setField(leaf, "fulfillmentMode", FulfillmentMode.ON_SITE);
        ReflectionTestUtils.setField(leaf, "active", true);
        return leaf;
    }
}
