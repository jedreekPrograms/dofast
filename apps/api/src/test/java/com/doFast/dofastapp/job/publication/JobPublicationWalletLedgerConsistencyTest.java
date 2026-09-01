package com.doFast.dofastapp.job.publication;

import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.job.category.FulfillmentMode;
import com.doFast.dofastapp.job.category.JobCategory;
import com.doFast.dofastapp.job.category.JobCategoryRepository;
import com.doFast.dofastapp.job.dto.JobRequest;
import com.doFast.dofastapp.job.publication.dto.CreateJobPublicationRequest;
import com.doFast.dofastapp.job.service.JobService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobPublicationWalletLedgerConsistencyTest {

    @Mock private JobPublicationRepository publicationRepository;
    @Mock private UserRepository userRepository;
    @Mock private JobCategoryRepository categoryRepository;
    @Mock private RouteQuoteService routeQuoteService;
    @Mock private WalletService walletService;
    @Mock private JobService jobService;
    @Mock private ObjectMapper objectMapper;

    private JobPublicationService service;
    private User user;

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
    }

    @Test
    void reservationLedgerMismatchFailsBeforePaymentRequiredPublicationIsPersisted() {
        JobRequest job = onSiteJob("70.00");
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        when(objectMapper.writeValueAsString(job)).thenReturn("payload-ledger-mismatch");
        when(categoryRepository.findByIdAndActiveTrue(42L)).thenReturn(Optional.of(onSiteLeafCategory(42L)));
        when(walletService.getBalanceForUpdate(7L)).thenReturn(new BigDecimal("25.00"));
        when(walletService.debit(
                7L,
                new BigDecimal("25.00"),
                WalletTransactionType.JOB_PUBLICATION_RESERVE,
                null,
                "job-publication:7:req-ledger-mismatch:reserve"
        )).thenReturn(false);

        ConflictException error = assertThrows(
                ConflictException.class,
                () -> service.create(new CreateJobPublicationRequest("req-ledger-mismatch", job), user)
        );

        assertEquals("Wykryto niespójny stan rezerwacji publikacji zlecenia", error.getMessage());
        verify(publicationRepository, never()).save(any(JobPublication.class));
        verifyNoInteractions(jobService);
    }

    @Test
    void cancellationLedgerMismatchLeavesPublicationPayableAndUnpersisted() {
        JobPublication publication = pendingPublication("job-publication:7:req-cancel", 11L);
        when(publicationRepository.findOwnedByIdForUpdate(11L, 7L)).thenReturn(Optional.of(publication));
        when(walletService.creditRestoringOperation(
                7L,
                new BigDecimal("25.00"),
                WalletTransactionType.JOB_PUBLICATION_RELEASE,
                null,
                "job-publication:11:release",
                "job-publication:7:req-cancel:reserve"
        )).thenReturn(false);

        ConflictException error = assertThrows(ConflictException.class, () -> service.cancel(11L, user));

        assertEquals("Wykryto niespójny stan zwrotu rezerwacji publikacji zlecenia", error.getMessage());
        assertEquals(JobPublicationStatus.PAYMENT_REQUIRED, publication.getStatus());
        verify(publicationRepository, never()).save(any(JobPublication.class));
    }

    @Test
    void expiryLedgerMismatchLeavesPublicationPayableAndUnpersisted() {
        JobPublication publication = pendingPublication("job-publication:7:req-expire", 12L);
        ReflectionTestUtils.setField(publication, "expiresAt", LocalDateTime.now().minusMinutes(1));
        when(walletService.creditRestoringOperation(
                7L,
                new BigDecimal("25.00"),
                WalletTransactionType.JOB_PUBLICATION_RELEASE,
                null,
                "job-publication:12:release",
                "job-publication:7:req-expire:reserve"
        )).thenReturn(false);

        ConflictException error = assertThrows(
                ConflictException.class,
                () -> service.expireIfNecessary(publication, LocalDateTime.now())
        );

        assertEquals("Wykryto niespójny stan zwrotu rezerwacji publikacji zlecenia", error.getMessage());
        assertEquals(JobPublicationStatus.PAYMENT_REQUIRED, publication.getStatus());
        verify(publicationRepository, never()).save(any(JobPublication.class));
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

    private JobPublication pendingPublication(String requestKey, Long id) {
        JobPublication publication = new JobPublication();
        publication.initializePaymentRequired(
                user,
                requestKey,
                "hash",
                "payload",
                42L,
                null,
                new BigDecimal("70.00"),
                new BigDecimal("25.00"),
                new BigDecimal("45.00"),
                LocalDateTime.now(),
                LocalDateTime.now().plusMinutes(10)
        );
        ReflectionTestUtils.setField(publication, "id", id);
        return publication;
    }
}
