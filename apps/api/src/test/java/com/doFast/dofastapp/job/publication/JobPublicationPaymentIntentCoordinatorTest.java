package com.doFast.dofastapp.job.publication;

import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.job.category.JobCategoryRepository;
import com.doFast.dofastapp.job.service.JobService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobPublicationPaymentIntentCoordinatorTest {

    @Mock private JobPublicationRepository publicationRepository;
    @Mock private UserRepository userRepository;
    @Mock private JobCategoryRepository categoryRepository;
    @Mock private RouteQuoteService routeQuoteService;
    @Mock private WalletService walletService;
    @Mock private JobService jobService;
    @Mock private ObjectMapper objectMapper;
    @Mock private JobPublicationPaymentIntentService paymentIntentService;

    private JobPublicationPaymentIntentCoordinator coordinator;
    private User user;

    @BeforeEach
    void setUp() {
        JobPublicationService publicationService = new JobPublicationService(
                publicationRepository,
                userRepository,
                categoryRepository,
                routeQuoteService,
                walletService,
                jobService,
                objectMapper
        );
        coordinator = new JobPublicationPaymentIntentCoordinator(publicationService, paymentIntentService);
        user = new User("owner@example.com", "owner");
        ReflectionTestUtils.setField(user, "id", 7L);
    }

    @Test
    void releasesExpiredReservationSourcesBeforeRejectingPaymentIntentCreation() {
        LocalDateTime now = LocalDateTime.now();
        JobPublication publication = new JobPublication();
        publication.initializePaymentRequired(
                user,
                "job-publication:7:req-expired",
                "hash-expired",
                "private-payload",
                42L,
                null,
                new BigDecimal("70.00"),
                new BigDecimal("25.00"),
                new BigDecimal("45.00"),
                now.minusMinutes(12),
                now.minusMinutes(1)
        );
        ReflectionTestUtils.setField(publication, "id", 10L);

        when(publicationRepository.findOwnedByIdForUpdate(10L, 7L)).thenReturn(Optional.of(publication));
        when(walletService.creditRestoringOperation(
                7L,
                new BigDecimal("25.00"),
                WalletTransactionType.JOB_PUBLICATION_RELEASE,
                null,
                "job-publication:10:release",
                "job-publication:7:req-expired:reserve"
        )).thenReturn(true);

        assertThrows(ConflictException.class, () -> coordinator.create(10L, user));

        assertEquals(JobPublicationStatus.CANCELLED, publication.getStatus());
        assertEquals(null, publication.getRequestPayload());
        verify(walletService).creditRestoringOperation(
                7L,
                new BigDecimal("25.00"),
                WalletTransactionType.JOB_PUBLICATION_RELEASE,
                null,
                "job-publication:10:release",
                "job-publication:7:req-expired:reserve"
        );
        verify(publicationRepository).save(publication);
        verify(paymentIntentService, never()).create(10L, user);
    }
}
