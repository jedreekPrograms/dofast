package com.doFast.dofastapp.job.publication;

import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobPublicationPrivacyTest {

    @Mock private JobPublicationRepository publicationRepository;
    @Mock private UserRepository userRepository;
    @Mock private JobCategoryRepository categoryRepository;
    @Mock private RouteQuoteService routeQuoteService;
    @Mock private WalletService walletService;
    @Mock private JobService jobService;
    @Mock private ObjectMapper objectMapper;

    private JobPublicationService service;
    private User owner;
    private User outsider;

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
        owner = user(7L, "owner");
        outsider = user(8L, "outsider");
    }

    @Test
    void outsiderGetsNeutralNotFoundForExistingPrivatePublicationRead() {
        when(publicationRepository.findOwnedByIdForUpdate(99L, 8L)).thenReturn(Optional.empty());

        ResourceNotFoundException error = assertThrows(
                ResourceNotFoundException.class,
                () -> service.get(99L, outsider)
        );

        assertEquals("Publikacja nie istnieje", error.getMessage());
        verify(publicationRepository, never()).findByIdForUpdate(99L);
    }

    @Test
    void outsiderGetsNeutralNotFoundForCancellationWithoutWalletSideEffects() {
        when(publicationRepository.findOwnedByIdForUpdate(99L, 8L)).thenReturn(Optional.empty());

        ResourceNotFoundException error = assertThrows(
                ResourceNotFoundException.class,
                () -> service.cancel(99L, outsider)
        );

        assertEquals("Publikacja nie istnieje", error.getMessage());
        verify(publicationRepository, never()).findByIdForUpdate(99L);
        verify(walletService, never()).creditRestoringOperation(any(), any(), any(), any(), any(), any());
    }

    @Test
    void ownerStillReadsPrivateFundingStateThroughScopedLookup() {
        JobPublication publication = paymentRequiredPublication();
        when(publicationRepository.findOwnedByIdForUpdate(99L, 7L)).thenReturn(Optional.of(publication));

        var response = service.get(99L, owner);

        assertEquals(99L, response.id());
        assertEquals(JobPublicationStatus.PAYMENT_REQUIRED, response.status());
        assertEquals(new BigDecimal("25.00"), response.walletReservedAmount());
        assertEquals(new BigDecimal("45.00"), response.paymentAmount());
        verify(publicationRepository, never()).findByIdForUpdate(99L);
    }

    private JobPublication paymentRequiredPublication() {
        JobPublication publication = new JobPublication();
        publication.initializePaymentRequired(
                owner,
                "job-publication:7:req-private",
                "hash",
                "private-payload",
                42L,
                null,
                new BigDecimal("70.00"),
                new BigDecimal("25.00"),
                new BigDecimal("45.00"),
                LocalDateTime.now(),
                LocalDateTime.now().plusMinutes(10)
        );
        ReflectionTestUtils.setField(publication, "id", 99L);
        return publication;
    }

    private User user(Long id, String nickname) {
        User user = new User(nickname + "@example.com", nickname);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
