package com.doFast.dofastapp.job.publication;

import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.job.category.FulfillmentMode;
import com.doFast.dofastapp.job.category.JobCategory;
import com.doFast.dofastapp.job.category.JobCategoryRepository;
import com.doFast.dofastapp.job.dto.JobRequest;
import com.doFast.dofastapp.job.dto.JobResponse;
import com.doFast.dofastapp.job.publication.dto.CreateJobPublicationRequest;
import com.doFast.dofastapp.location.routing.dto.RoutePointRequest;
import com.doFast.dofastapp.location.routing.service.RouteQuoteService;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.repository.UserRepository;
import com.doFast.dofastapp.wallet.enums.WalletTransactionType;
import com.doFast.dofastapp.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobPublicationServiceTest {

    @Mock private JobPublicationRepository publicationRepository;
    @Mock private UserRepository userRepository;
    @Mock private JobCategoryRepository categoryRepository;
    @Mock private RouteQuoteService routeQuoteService;
    @Mock private WalletService walletService;
    @Mock private com.doFast.dofastapp.job.service.JobService jobService;
    @Mock private ObjectMapper objectMapper;

    private JobPublicationService service;
    private User user;
    private JobCategory category;

    @BeforeEach
    void setUp() {
        service = new JobPublicationService(
                publicationRepository,
                userRepository,
                categoryRepository,
                routeQuoteService,
                walletService,
                jobService,
                objectMapper
        );
        user = user(7L);
        category = onSiteLeafCategory(42L);
    }

    @Test
    void publicationCreationFailsClosedBeforeLocksOrSerializationWithoutIdentity() {
        User transientUser = new User("transient@example.com", "transient");
        CreateJobPublicationRequest request = new CreateJobPublicationRequest("req-transient", onSiteJob("70.00"));

        assertThrows(ForbiddenOperationException.class, () -> service.create(request, transientUser));

        verifyNoInteractions(
                publicationRepository,
                userRepository,
                categoryRepository,
                routeQuoteService,
                walletService,
                jobService,
                objectMapper
        );
    }

    @Test
    void partialWalletReservesExactlyAvailableBalanceAndChargesOnlyShortfall() {
        JobRequest job = onSiteJob("70.00");
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        when(objectMapper.writeValueAsString(job)).thenReturn("payload-70");
        when(categoryRepository.findByIdAndActiveTrue(42L)).thenReturn(Optional.of(category));
        when(walletService.getBalanceForUpdate(7L)).thenReturn(new BigDecimal("25.00"));
        when(publicationRepository.save(any(JobPublication.class))).thenAnswer(invocation -> {
            JobPublication publication = invocation.getArgument(0);
            ReflectionTestUtils.setField(publication, "id", 11L);
            return publication;
        });
        when(walletService.debit(
                7L,
                new BigDecimal("25.00"),
                WalletTransactionType.JOB_PUBLICATION_RESERVE,
                null,
                "job-publication:7:req-1:reserve"
        )).thenReturn(true);

        var response = service.create(new CreateJobPublicationRequest("req-1", job), user);

        assertEquals(11L, response.id());
        assertEquals(JobPublicationStatus.PAYMENT_REQUIRED, response.status());
        assertEquals(new BigDecimal("70.00"), response.totalAmount());
        assertEquals(new BigDecimal("25.00"), response.walletReservedAmount());
        assertEquals(new BigDecimal("45.00"), response.missingAmount());
        assertEquals(new BigDecimal("45.00"), response.paymentAmount());
        verify(jobService, never()).createJob(any(), any());
    }

    @Test
    void onlinePaymentMinimumReducesWalletReservationInsteadOfOverfunding() {
        JobRequest job = onSiteJob("25.50");
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        when(objectMapper.writeValueAsString(job)).thenReturn("payload-minimum");
        when(categoryRepository.findByIdAndActiveTrue(42L)).thenReturn(Optional.of(category));
        when(walletService.getBalanceForUpdate(7L)).thenReturn(new BigDecimal("25.00"));
        when(publicationRepository.save(any(JobPublication.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(walletService.debit(
                7L,
                new BigDecimal("24.50"),
                WalletTransactionType.JOB_PUBLICATION_RESERVE,
                null,
                "job-publication:7:req-min:reserve"
        )).thenReturn(true);

        var response = service.create(new CreateJobPublicationRequest("req-min", job), user);

        assertEquals(new BigDecimal("24.50"), response.walletReservedAmount());
        assertEquals(new BigDecimal("1.00"), response.missingAmount());
        assertEquals(new BigDecimal("1.00"), response.paymentAmount());
        verify(walletService).debit(
                7L,
                new BigDecimal("24.50"),
                WalletTransactionType.JOB_PUBLICATION_RESERVE,
                null,
                "job-publication:7:req-min:reserve"
        );
    }

    @Test
    void repeatedRequestIdWithSamePayloadDoesNotReserveTwice() {
        JobRequest job = onSiteJob("70.00");
        JobPublication existing = pendingPublication(user, 11L, "job-publication:7:req-replay", "same-hash", "70.00", "25.00", "45.00");
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        when(objectMapper.writeValueAsString(job)).thenReturn("same-payload");
        String actualHash = hashThroughServiceFixture("same-payload");
        ReflectionTestUtils.setField(existing, "payloadHash", actualHash);
        when(publicationRepository.findByRequestKey("job-publication:7:req-replay")).thenReturn(Optional.of(existing));

        var response = service.create(new CreateJobPublicationRequest("req-replay", job), user);

        assertEquals(11L, response.id());
        verify(walletService, never()).getBalanceForUpdate(any());
        verify(walletService, never()).debit(any(), any(), any(), any(), anyString());
        verify(jobService, never()).createJob(any(), any());
    }

    @Test
    void repeatedExpiredRequestIdRestoresOriginalSourcesAndReturnsTerminalState() {
        JobRequest job = onSiteJob("70.00");
        JobPublication existing = pendingPublication(user, 11L, "job-publication:7:req-expired", "same-hash", "70.00", "25.00", "45.00");
        ReflectionTestUtils.setField(existing, "expiresAt", LocalDateTime.now().minusSeconds(1));
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        when(objectMapper.writeValueAsString(job)).thenReturn("same-payload");
        ReflectionTestUtils.setField(existing, "payloadHash", hashThroughServiceFixture("same-payload"));
        when(publicationRepository.findByRequestKey("job-publication:7:req-expired")).thenReturn(Optional.of(existing));
        when(publicationRepository.save(existing)).thenReturn(existing);
        when(walletService.creditRestoringOperation(
                7L,
                new BigDecimal("25.00"),
                WalletTransactionType.JOB_PUBLICATION_RELEASE,
                null,
                "job-publication:11:release",
                "job-publication:7:req-expired:reserve"
        )).thenReturn(true);

        var response = service.create(new CreateJobPublicationRequest("req-expired", job), user);

        assertEquals(JobPublicationStatus.CANCELLED, response.status());
        verify(walletService, times(1)).creditRestoringOperation(
                7L,
                new BigDecimal("25.00"),
                WalletTransactionType.JOB_PUBLICATION_RELEASE,
                null,
                "job-publication:11:release",
                "job-publication:7:req-expired:reserve"
        );
        verify(walletService, never()).getBalanceForUpdate(any());
        verify(walletService, never()).debit(any(), any(), any(), any(), anyString());
        verify(jobService, never()).createJob(any(), any());
    }

    @Test
    void repeatedRequestIdWithDifferentPayloadIsConflict() {
        JobRequest job = onSiteJob("70.00");
        JobPublication existing = pendingPublication(user, 11L, "job-publication:7:req-conflict", "different-hash", "70.00", "25.00", "45.00");
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        when(objectMapper.writeValueAsString(job)).thenReturn("new-payload");
        when(publicationRepository.findByRequestKey("job-publication:7:req-conflict")).thenReturn(Optional.of(existing));

        assertThrows(
                ConflictException.class,
                () -> service.create(new CreateJobPublicationRequest("req-conflict", job), user)
        );
        verify(walletService, never()).getBalanceForUpdate(any());
    }

    @Test
    void cancelRestoresReservationSourcesExactlyOnce() {
        JobPublication publication = pendingPublication(user, 11L, "job-publication:7:req-cancel", "hash", "70.00", "25.00", "45.00");
        when(publicationRepository.findOwnedByIdForUpdate(11L, 7L)).thenReturn(Optional.of(publication));
        when(publicationRepository.save(publication)).thenReturn(publication);
        when(walletService.creditRestoringOperation(
                7L,
                new BigDecimal("25.00"),
                WalletTransactionType.JOB_PUBLICATION_RELEASE,
                null,
                "job-publication:11:release",
                "job-publication:7:req-cancel:reserve"
        )).thenReturn(true);

        var first = service.cancel(11L, user);
        var second = service.cancel(11L, user);

        assertEquals(JobPublicationStatus.CANCELLED, first.status());
        assertEquals(JobPublicationStatus.CANCELLED, second.status());
        verify(walletService, times(1)).creditRestoringOperation(
                7L,
                new BigDecimal("25.00"),
                WalletTransactionType.JOB_PUBLICATION_RELEASE,
                null,
                "job-publication:11:release",
                "job-publication:7:req-cancel:reserve"
        );
    }

    @Test
    void fullWalletPublishesImmediatelyWithoutPublicationReservation() {
        JobRequest job = onSiteJob("70.00");
        JobResponse created = org.mockito.Mockito.mock(JobResponse.class);
        when(created.id()).thenReturn(99L);
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        when(objectMapper.writeValueAsString(job)).thenReturn("payload-full");
        when(categoryRepository.findByIdAndActiveTrue(42L)).thenReturn(Optional.of(category));
        when(walletService.getBalanceForUpdate(7L)).thenReturn(new BigDecimal("70.00"));
        when(jobService.createJob(job, user)).thenReturn(created);
        when(publicationRepository.save(any(JobPublication.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(new CreateJobPublicationRequest("req-full", job), user);

        assertEquals(JobPublicationStatus.PUBLISHED, response.status());
        assertEquals(99L, response.jobId());
        assertEquals(new BigDecimal("0.00"), response.paymentAmount());
        verify(walletService, never()).debit(
                any(),
                any(),
                eq(WalletTransactionType.JOB_PUBLICATION_RESERVE),
                any(),
                anyString()
        );
    }

    private User user(Long id) {
        User value = new User("owner@example.com", "owner");
        ReflectionTestUtils.setField(value, "id", id);
        return value;
    }

    private JobCategory onSiteLeafCategory(Long id) {
        JobCategory parent = new JobCategory();
        ReflectionTestUtils.setField(parent, "id", 1L);
        JobCategory leaf = new JobCategory();
        ReflectionTestUtils.setField(leaf, "id", id);
        ReflectionTestUtils.setField(leaf, "parent", parent);
        ReflectionTestUtils.setField(leaf, "slug", "montaz-mebli");
        ReflectionTestUtils.setField(leaf, "name", "Montaż mebli");
        ReflectionTestUtils.setField(leaf, "fulfillmentMode", FulfillmentMode.ON_SITE);
        ReflectionTestUtils.setField(leaf, "active", true);
        return leaf;
    }

    private JobRequest onSiteJob(String price) {
        JobRequest request = new JobRequest();
        request.setTitle("Montaż szafy");
        request.setDescription("Montaż dużej szafy w mieszkaniu.");
        request.setPrice(new BigDecimal(price));
        request.setCategoryId(42L);
        request.setLocation(new RoutePointRequest(
                new BigDecimal("51.1100"),
                new BigDecimal("17.0300"),
                "Wrocław",
                "ul. Testowa 1, Wrocław",
                null
        ));
        return request;
    }

    private JobPublication pendingPublication(
            User owner,
            Long id,
            String requestKey,
            String payloadHash,
            String total,
            String reserved,
            String payment
    ) {
        JobPublication publication = new JobPublication();
        publication.initializePaymentRequired(
                owner,
                requestKey,
                payloadHash,
                "payload",
                42L,
                null,
                new BigDecimal(total),
                new BigDecimal(reserved),
                new BigDecimal(payment),
                LocalDateTime.now(),
                LocalDateTime.now().plusMinutes(10)
        );
        ReflectionTestUtils.setField(publication, "id", id);
        return publication;
    }

    private String hashThroughServiceFixture(String payload) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
