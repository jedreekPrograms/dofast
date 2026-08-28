package com.doFast.dofastapp.job.publication;

import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.job.category.JobCategoryRepository;
import com.doFast.dofastapp.job.service.JobService;
import com.doFast.dofastapp.location.routing.service.RouteQuoteService;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.repository.UserRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobPublicationRecoveryServiceTest {

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
        user = new User("owner@example.com", "owner");
        ReflectionTestUtils.setField(user, "id", 7L);
    }

    @Test
    void returnsOnlyRepositorySelectedRecoverablePublicationsForOwner() {
        LocalDateTime now = LocalDateTime.now();
        JobPublication newest = pending(12L, now.minusMinutes(1), now.plusMinutes(8));
        JobPublication older = pending(11L, now.minusMinutes(2), now.plusMinutes(7));

        when(publicationRepository.findAllByUser_IdAndStatusAndExpiresAtAfterOrderByCreatedAtDescIdDesc(
                eq(7L),
                eq(JobPublicationStatus.PAYMENT_REQUIRED),
                any(LocalDateTime.class)
        )).thenReturn(List.of(newest, older));

        var result = service.getRecoverable(user);

        assertEquals(List.of(12L, 11L), result.stream().map(response -> response.id()).toList());
        assertEquals(JobPublicationStatus.PAYMENT_REQUIRED, result.get(0).status());
        assertEquals(new BigDecimal("45.00"), result.get(0).paymentAmount());
        verify(publicationRepository).findAllByUser_IdAndStatusAndExpiresAtAfterOrderByCreatedAtDescIdDesc(
                eq(7L),
                eq(JobPublicationStatus.PAYMENT_REQUIRED),
                any(LocalDateTime.class)
        );
    }

    @Test
    void anonymousCallerCannotEnumeratePendingPublications() {
        assertThrows(ForbiddenOperationException.class, () -> service.getRecoverable(null));

        verify(publicationRepository, never())
                .findAllByUser_IdAndStatusAndExpiresAtAfterOrderByCreatedAtDescIdDesc(any(), any(), any());
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
