package com.doFast.dofastapp.job.publication;

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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobPublicationRecoveryCoordinatorTest {

    @Mock private JobPublicationRepository publicationRepository;
    @Mock private UserRepository userRepository;
    @Mock private JobCategoryRepository categoryRepository;
    @Mock private RouteQuoteService routeQuoteService;
    @Mock private WalletService walletService;
    @Mock private JobService jobService;
    @Mock private ObjectMapper objectMapper;

    private JobPublicationRecoveryCoordinator coordinator;
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
        coordinator = new JobPublicationRecoveryCoordinator(publicationRepository, publicationService);
        user = new User("owner@example.com", "owner");
        ReflectionTestUtils.setField(user, "id", 7L);
    }

    @Test
    void expiresStaleOwnerPublicationBeforeReturningRecoverableOnes() {
        LocalDateTime now = LocalDateTime.now();
        JobPublication expired = pending(10L, now.minusMinutes(12), now.minusMinutes(1));
        JobPublication active = pending(11L, now.minusMinutes(2), now.plusMinutes(7));

        when(publicationRepository.findFirstByUser_IdAndStatusAndExpiresAtBeforeOrderByExpiresAtAscIdAsc(
                eq(7L),
                eq(JobPublicationStatus.PAYMENT_REQUIRED),
                any(LocalDateTime.class)
        )).thenReturn(Optional.of(expired), Optional.empty());
        when(publicationRepository.findAllByUser_IdAndStatusAndExpiresAtAfterOrderByCreatedAtDescIdDesc(
                eq(7L),
                eq(JobPublicationStatus.PAYMENT_REQUIRED),
                any(LocalDateTime.class)
        )).thenReturn(List.of(active));
        when(walletService.creditRestoringOperation(
                eq(7L),
                eq(new BigDecimal("25.00")),
                eq(WalletTransactionType.JOB_PUBLICATION_RELEASE),
                eq(null),
                eq("job-publication:10:release"),
                eq("job-publication:7:req-10:reserve")
        )).thenReturn(true);

        var result = coordinator.getRecoverable(user);

        assertEquals(List.of(11L), result.stream().map(response -> response.id()).toList());
        assertEquals(JobPublicationStatus.CANCELLED, expired.getStatus());
        verify(walletService).creditRestoringOperation(
                7L,
                new BigDecimal("25.00"),
                WalletTransactionType.JOB_PUBLICATION_RELEASE,
                null,
                "job-publication:10:release",
                "job-publication:7:req-10:reserve"
        );
        verify(publicationRepository).save(expired);
    }

    private JobPublication pending(Long id, LocalDateTime createdAt, LocalDateTime expiresAt) {
        JobPublication publication = new JobPublication();
        publication.initializePaymentRequired(
                user,
                "job-publication:7:req-" + id,
                "hash-" + id,
                "private-payload",
                42L,
                null,
                new BigDecimal("70.00"),
                new BigDecimal("25.00"),
                new BigDecimal("45.00"),
                createdAt,
                expiresAt
        );
        ReflectionTestUtils.setField(publication, "id", id);
        return publication;
    }
}
