package com.doFast.dofastapp.job.publication;

import com.doFast.dofastapp.common.exception.ConflictException;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobPublicationStripeSettlementServiceTest {

    @Mock private JobPublicationRepository publicationRepository;
    @Mock private JobPublicationService publicationService;
    @Mock private StripePaymentService stripePaymentService;
    @Mock private JobCategoryRepository categoryRepository;
    @Mock private RouteQuoteRepository routeQuoteRepository;
    @Mock private JobService jobService;

    private JobPublicationStripeSettlementService service;
    private User user;
    private JobCategory category;

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
        user = new User("owner@example.com", "owner");
        ReflectionTestUtils.setField(user, "id", 7L);
        category = onSiteLeafCategory();
    }

    @Test
    void successfulShortfallPaymentPublishesExactlyOnce() {
        JobPublication publication = pendingPublication(LocalDateTime.now().plusMinutes(5));
        JobRequest jobRequest = new JobRequest();
        JobResponse created = org.mockito.Mockito.mock(JobResponse.class);
        when(created.id()).thenReturn(99L);
        when(publicationRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(publication));
        when(stripePaymentService.processSuccessfulJobPublicationPayment(
                any(PaymentIntent.class), eq("evt_1"), eq(11L)
        )).thenReturn(true);
        when(categoryRepository.findByIdAndActiveTrue(42L)).thenReturn(Optional.of(category));
        when(publicationService.deserialize("payload")).thenReturn(jobRequest);
        when(jobService.createJob(jobRequest, user)).thenReturn(created);

        boolean processed = service.processSuccessfulPayment(paymentIntent("pi_1", 4500L), "evt_1");

        assertTrue(processed);
        assertEquals(JobPublicationStatus.PUBLISHED, publication.getStatus());
        assertEquals(99L, publication.getPublishedJobId());
        verify(publicationService).restoreReservation(publication);
        verify(jobService).createJob(jobRequest, user);
        verify(publicationRepository).save(publication);

        when(stripePaymentService.processSuccessfulJobPublicationPayment(
                any(PaymentIntent.class), eq("evt_retry"), eq(11L)
        )).thenReturn(false);
        service.processSuccessfulPayment(paymentIntent("pi_1", 4500L), "evt_retry");
        verify(jobService).createJob(jobRequest, user);
    }

    @Test
    void latePaymentAfterCancellationCreditsPaymentButNeverRecreatesJob() {
        JobPublication publication = pendingPublication(LocalDateTime.now().plusMinutes(5));
        publication.cancel(LocalDateTime.now());
        when(publicationRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(publication));
        when(stripePaymentService.processSuccessfulJobPublicationPayment(
                any(PaymentIntent.class), eq("evt_late"), eq(11L)
        )).thenReturn(true);

        assertTrue(service.processSuccessfulPayment(paymentIntent("pi_1", 4500L), "evt_late"));

        assertEquals(JobPublicationStatus.CANCELLED, publication.getStatus());
        verify(jobService, never()).createJob(any(), any());
        verify(publicationService, never()).restoreReservation(publication);
    }

    @Test
    void paymentAfterPublicationExpiryBecomesWalletFundingWithoutPublishing() {
        JobPublication publication = pendingPublication(LocalDateTime.now().minusSeconds(1));
        when(publicationRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(publication));
        when(stripePaymentService.processSuccessfulJobPublicationPayment(
                any(PaymentIntent.class), eq("evt_expired"), eq(11L)
        )).thenReturn(true);

        assertTrue(service.processSuccessfulPayment(paymentIntent("pi_1", 4500L), "evt_expired"));

        assertEquals(JobPublicationStatus.PAYMENT_RECEIVED, publication.getStatus());
        verify(publicationService).restoreReservation(publication);
        verify(jobService, never()).createJob(any(), any());
        verify(publicationRepository).save(publication);
    }

    @Test
    void mismatchedStripeAmountIsRejectedBeforeLedgerMutation() {
        JobPublication publication = pendingPublication(LocalDateTime.now().plusMinutes(5));
        when(publicationRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(publication));

        assertThrows(
                ConflictException.class,
                () -> service.processSuccessfulPayment(paymentIntent("pi_1", 4400L), "evt_bad")
        );

        verify(stripePaymentService, never()).processSuccessfulJobPublicationPayment(any(), any(), any());
        verify(jobService, never()).createJob(any(), any());
    }

    private JobPublication pendingPublication(LocalDateTime expiresAt) {
        JobPublication publication = new JobPublication();
        publication.initializePaymentRequired(
                user,
                "job-publication:7:req-1",
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
        publication.attachStripePaymentIntent("pi_1", LocalDateTime.now());
        return publication;
    }

    private PaymentIntent paymentIntent(String id, long amountInCents) {
        PaymentIntent intent = new PaymentIntent();
        intent.setId(id);
        intent.setAmount(amountInCents);
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
