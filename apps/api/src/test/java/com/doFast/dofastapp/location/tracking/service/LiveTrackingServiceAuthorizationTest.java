package com.doFast.dofastapp.location.tracking.service;

import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.repository.JobRepository;
import com.doFast.dofastapp.location.routing.provider.RouteProvider;
import com.doFast.dofastapp.location.tracking.dto.LiveLocationUpdateRequest;
import com.doFast.dofastapp.location.tracking.repository.JobLiveTrackingRepository;
import com.doFast.dofastapp.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LiveTrackingServiceAuthorizationTest {

    @Test
    void hidesExistingJobFromUnassignedGpsPublisherBeforeTrackingStateIsRead() {
        Fixture fixture = fixture();
        User outsider = mock(User.class);
        when(outsider.getId()).thenReturn(99L);
        when(fixture.jobRepository.findAssignedWorkerByIdForUpdate(42L, 99L)).thenReturn(Optional.empty());
        when(fixture.jobRepository.findByIdAndCreatedBy_Id(42L, 99L)).thenReturn(Optional.empty());

        ResourceNotFoundException error = assertThrows(
                ResourceNotFoundException.class,
                () -> fixture.service.updateLocation(42L, request(), outsider)
        );

        assertEquals("Zlecenie nie istnieje", error.getMessage());
        verify(fixture.jobRepository).findAssignedWorkerByIdForUpdate(42L, 99L);
        verify(fixture.jobRepository).findByIdAndCreatedBy_Id(42L, 99L);
        verify(fixture.jobRepository, never()).findByIdForUpdate(any());
        verifyNoInteractions(fixture.trackingRepository, fixture.positionSanityValidator, fixture.updateRateLimiter);
    }

    @Test
    void preservesForbiddenContractForKnownJobOwnerWithoutReadingTrackingState() {
        Fixture fixture = fixture();
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(11L);
        when(fixture.jobRepository.findAssignedWorkerByIdForUpdate(42L, 11L)).thenReturn(Optional.empty());
        when(fixture.jobRepository.findByIdAndCreatedBy_Id(42L, 11L)).thenReturn(Optional.of(mock(Job.class)));

        ForbiddenOperationException error = assertThrows(
                ForbiddenOperationException.class,
                () -> fixture.service.updateLocation(42L, request(), owner)
        );

        assertEquals("Tylko przypisany wykonawca może udostępniać lokalizację", error.getMessage());
        verify(fixture.jobRepository).findAssignedWorkerByIdForUpdate(42L, 11L);
        verify(fixture.jobRepository).findByIdAndCreatedBy_Id(42L, 11L);
        verify(fixture.jobRepository, never()).findByIdForUpdate(any());
        verifyNoInteractions(fixture.trackingRepository, fixture.positionSanityValidator, fixture.updateRateLimiter);
    }

    @Test
    void rejectsMissingPersistedGpsPublisherIdentityBeforeRepositoryAccess() {
        Fixture fixture = fixture();
        User transientUser = mock(User.class);
        when(transientUser.getId()).thenReturn(null);

        ResourceNotFoundException error = assertThrows(
                ResourceNotFoundException.class,
                () -> fixture.service.updateLocation(42L, request(), transientUser)
        );

        assertEquals("Zlecenie nie istnieje", error.getMessage());
        verifyNoInteractions(fixture.jobRepository, fixture.trackingRepository,
                fixture.positionSanityValidator, fixture.updateRateLimiter);
    }

    @Test
    void checksTrackingLifecycleOnlyAfterAssignedWorkerScopedLockSucceeds() {
        Fixture fixture = fixture();
        User worker = mock(User.class);
        when(worker.getId()).thenReturn(7L);

        Job terminalJob = mock(Job.class);
        when(terminalJob.getStatus()).thenReturn(JobStatus.DONE);
        when(fixture.jobRepository.findAssignedWorkerByIdForUpdate(42L, 7L)).thenReturn(Optional.of(terminalJob));

        assertThrows(ConflictException.class, () -> fixture.service.updateLocation(42L, request(), worker));

        verify(fixture.jobRepository).findAssignedWorkerByIdForUpdate(42L, 7L);
        verify(fixture.jobRepository, never()).findByIdForUpdate(any());
        verifyNoInteractions(fixture.trackingRepository, fixture.positionSanityValidator, fixture.updateRateLimiter);
    }

    private Fixture fixture() {
        JobRepository jobRepository = mock(JobRepository.class);
        JobLiveTrackingRepository trackingRepository = mock(JobLiveTrackingRepository.class);
        LiveTrackingAccessService accessService = mock(LiveTrackingAccessService.class);
        TrackingPositionSanityValidator positionSanityValidator = mock(TrackingPositionSanityValidator.class);
        TrackingUpdateRateLimiter updateRateLimiter = mock(TrackingUpdateRateLimiter.class);
        RouteProvider routeProvider = mock(RouteProvider.class);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        TransactionStatus transactionStatus = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);

        LiveTrackingService service = new LiveTrackingService(
                jobRepository,
                trackingRepository,
                accessService,
                positionSanityValidator,
                updateRateLimiter,
                routeProvider,
                messagingTemplate,
                transactionManager,
                30,
                150.0,
                20
        );
        return new Fixture(service, jobRepository, trackingRepository, positionSanityValidator, updateRateLimiter);
    }

    private LiveLocationUpdateRequest request() {
        return new LiveLocationUpdateRequest(
                new BigDecimal("51.1079"),
                new BigDecimal("17.0385"),
                8.0,
                null,
                null,
                Instant.now()
        );
    }

    private record Fixture(
            LiveTrackingService service,
            JobRepository jobRepository,
            JobLiveTrackingRepository trackingRepository,
            TrackingPositionSanityValidator positionSanityValidator,
            TrackingUpdateRateLimiter updateRateLimiter
    ) {}
}
