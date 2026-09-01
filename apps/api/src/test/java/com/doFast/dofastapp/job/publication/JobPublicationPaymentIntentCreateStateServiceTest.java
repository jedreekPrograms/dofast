package com.doFast.dofastapp.job.publication;

import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
import com.doFast.dofastapp.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobPublicationPaymentIntentCreateStateServiceTest {

    @Mock private JobPublicationRepository publicationRepository;

    private JobPublicationPaymentIntentCreateStateService stateService;
    private User owner;

    @BeforeEach
    void setUp() {
        stateService = new JobPublicationPaymentIntentCreateStateService(publicationRepository);
        owner = new User("owner@example.com", "owner");
        ReflectionTestUtils.setField(owner, "id", 7L);
    }

    @Test
    void prepareCommitsDurableCreateClaimBeforeReturningProviderCommand() {
        LocalDateTime now = LocalDateTime.now();
        JobPublication publication = paymentRequiredPublication(now, now.plusMinutes(10));
        when(publicationRepository.findOwnedByIdForUpdate(99L, 7L)).thenReturn(Optional.of(publication));

        JobPublicationPaymentIntentCreateCommand command = stateService.prepareForOwner(99L, owner);

        assertThat(command.publicationId()).isEqualTo(99L);
        assertThat(command.userId()).isEqualTo(7L);
        assertThat(command.amount()).isEqualByComparingTo("45.00");
        assertThat(command.idempotencyKey()).isEqualTo("dofast:job-publication:99");
        assertThat(command.hasExistingPaymentIntent()).isFalse();
        assertThat(publication.getStripePaymentIntentCreateStartedAt()).isNotNull();
        assertThat(publication.getStripePaymentIntentCreateAttemptCount()).isEqualTo(1);
        assertThat(publication.getStripePaymentIntentCreateNextAttemptAt()).isAfter(LocalDateTime.now());
        verify(publicationRepository).saveAndFlush(publication);
    }

    @Test
    void concurrentPrepareCannotStartSecondProviderCallWhileLeaseIsActive() {
        LocalDateTime now = LocalDateTime.now();
        JobPublication publication = paymentRequiredPublication(now, now.plusMinutes(10));
        publication.claimStripePaymentIntentCreate(now, now.plusMinutes(2));
        when(publicationRepository.findOwnedByIdForUpdate(99L, 7L)).thenReturn(Optional.of(publication));

        assertThatThrownBy(() -> stateService.prepareForOwner(99L, owner))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("już przygotowywana");
        assertThat(publication.getStripePaymentIntentCreateAttemptCount()).isEqualTo(1);
    }

    @Test
    void outsiderCannotEnumeratePublicationThroughPaymentPreparation() {
        User outsider = new User("outsider@example.com", "outsider");
        ReflectionTestUtils.setField(outsider, "id", 8L);
        when(publicationRepository.findOwnedByIdForUpdate(99L, 8L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> stateService.prepareForOwner(99L, outsider))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Publikacja nie istnieje");

        verify(publicationRepository, never()).findByIdForUpdate(99L);
        verify(publicationRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void cancelledOrphanPastSafeStripeIdempotencyWindowIsQuarantinedWithoutReplay() {
        LocalDateTime startedAt = LocalDateTime.now().minusHours(24);
        JobPublication publication = paymentRequiredPublication(startedAt, startedAt.plusMinutes(10));
        publication.claimStripePaymentIntentCreate(startedAt, startedAt.plusMinutes(2));
        publication.cancel(startedAt.plusMinutes(1));
        when(publicationRepository.findByIdForUpdate(99L)).thenReturn(Optional.of(publication));

        assertThat(stateService.claimCancelledRecovery(99L)).isEmpty();

        assertThat(publication.isStripePaymentIntentCreateReviewRequired()).isTrue();
        assertThat(publication.getStripePaymentIntentCreateNextAttemptAt()).isNull();
        assertThat(publication.getStripePaymentIntentCreateLastError()).isEqualTo("IDEMPOTENCY_WINDOW_EXPIRED");
        verify(publicationRepository).save(publication);
    }

    @Test
    void attachingProviderIntentAfterConcurrentCancellationArmsDurableCleanup() {
        LocalDateTime now = LocalDateTime.now();
        JobPublication publication = paymentRequiredPublication(now, now.plusMinutes(10));
        publication.claimStripePaymentIntentCreate(now, now.plusMinutes(2));
        publication.cancel(now.plusSeconds(5));
        when(publicationRepository.findByIdForUpdate(99L)).thenReturn(Optional.of(publication));

        JobPublicationPaymentIntentFinalizeStatus status = stateService.attachProviderIntent(99L, "pi_after_cancel");

        assertThat(status).isEqualTo(JobPublicationPaymentIntentFinalizeStatus.CANCELLED);
        assertThat(publication.getStripePaymentIntentId()).isEqualTo("pi_after_cancel");
        assertThat(publication.getStripePaymentIntentCleanupNextAttemptAt()).isNotNull();
        verify(publicationRepository).saveAndFlush(publication);
    }

    private JobPublication paymentRequiredPublication(LocalDateTime createdAt, LocalDateTime expiresAt) {
        JobPublication publication = new JobPublication();
        publication.initializePaymentRequired(
                owner,
                "job-publication:7:req-state-service",
                "hash",
                "private-payload",
                42L,
                null,
                new BigDecimal("70.00"),
                new BigDecimal("25.00"),
                new BigDecimal("45.00"),
                createdAt,
                expiresAt
        );
        ReflectionTestUtils.setField(publication, "id", 99L);
        return publication;
    }
}
