package com.doFast.dofastapp.location.tracking.service;

import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.repository.JobRepository;
import com.doFast.dofastapp.location.tracking.entity.JobLiveTracking;
import com.doFast.dofastapp.location.tracking.enums.TrackingPhase;
import com.doFast.dofastapp.location.tracking.repository.JobLiveTrackingRepository;
import com.doFast.dofastapp.user.entity.User;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrackingCheckpointGuardTest {

    @Test
    void validatesLockedSnapshotBeforeExecutingTransition() {
        JobRepository jobRepository = mock(JobRepository.class);
        JobLiveTrackingRepository trackingRepository = mock(JobLiveTrackingRepository.class);
        TrackingCheckpointProximityValidator proximityValidator = mock(TrackingCheckpointProximityValidator.class);
        TrackingCheckpointGuard guard = new TrackingCheckpointGuard(jobRepository, trackingRepository, proximityValidator);

        User worker = mock(User.class);
        when(worker.getId()).thenReturn(7L);

        Job job = mock(Job.class);
        Point target = mock(Point.class);
        when(job.getStatus()).thenReturn(JobStatus.IN_PROGRESS);
        when(job.getTakenBy()).thenReturn(worker);
        when(job.getLocation()).thenReturn(target);

        JobLiveTracking tracking = mock(JobLiveTracking.class);
        Point current = mock(Point.class);
        when(tracking.getPhase()).thenReturn(TrackingPhase.TO_ORIGIN);
        when(tracking.getCurrentLocation()).thenReturn(current);
        when(tracking.getAccuracyMeters()).thenReturn(8.0);

        when(jobRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(job));
        when(trackingRepository.findByJobIdForUpdate(42L)).thenReturn(Optional.of(tracking));

        AtomicBoolean executed = new AtomicBoolean(false);
        String result = guard.validateAndExecute(42L, worker, () -> {
            executed.set(true);
            return "ok";
        });

        assertEquals("ok", result);
        assertTrue(executed.get());
        verify(jobRepository).findByIdForUpdate(42L);
        verify(trackingRepository).findByJobIdForUpdate(42L);
        verify(proximityValidator).validate(any(), any(), any(), any(), any());
    }

    @Test
    void validatesDestinationProximityBeforeFinalArrival() {
        JobRepository jobRepository = mock(JobRepository.class);
        JobLiveTrackingRepository trackingRepository = mock(JobLiveTrackingRepository.class);
        TrackingCheckpointProximityValidator proximityValidator = mock(TrackingCheckpointProximityValidator.class);
        TrackingCheckpointGuard guard = new TrackingCheckpointGuard(jobRepository, trackingRepository, proximityValidator);

        User worker = mock(User.class);
        when(worker.getId()).thenReturn(7L);

        Job job = mock(Job.class);
        Point destination = mock(Point.class);
        when(job.getStatus()).thenReturn(JobStatus.IN_PROGRESS);
        when(job.getTakenBy()).thenReturn(worker);
        when(job.getDestinationLocation()).thenReturn(destination);

        JobLiveTracking tracking = mock(JobLiveTracking.class);
        Point current = mock(Point.class);
        when(tracking.getPhase()).thenReturn(TrackingPhase.TO_DESTINATION);
        when(tracking.getCurrentLocation()).thenReturn(current);
        when(tracking.getAccuracyMeters()).thenReturn(5.0);

        when(jobRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(job));
        when(trackingRepository.findByJobIdForUpdate(42L)).thenReturn(Optional.of(tracking));

        assertEquals("arrived", guard.validateAndExecute(42L, worker, () -> "arrived"));
        verify(proximityValidator).validate(eq(current), eq(5.0), any(), eq(destination), any());
    }

    @Test
    void rejectsDuplicateDestinationArrivalWithoutExecutingTransition() {
        JobRepository jobRepository = mock(JobRepository.class);
        JobLiveTrackingRepository trackingRepository = mock(JobLiveTrackingRepository.class);
        TrackingCheckpointProximityValidator proximityValidator = mock(TrackingCheckpointProximityValidator.class);
        TrackingCheckpointGuard guard = new TrackingCheckpointGuard(jobRepository, trackingRepository, proximityValidator);

        User worker = mock(User.class);
        when(worker.getId()).thenReturn(7L);

        Job job = mock(Job.class);
        when(job.getStatus()).thenReturn(JobStatus.IN_PROGRESS);
        when(job.getTakenBy()).thenReturn(worker);

        JobLiveTracking tracking = mock(JobLiveTracking.class);
        when(tracking.getPhase()).thenReturn(TrackingPhase.ARRIVED_DESTINATION);

        when(jobRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(job));
        when(trackingRepository.findByJobIdForUpdate(42L)).thenReturn(Optional.of(tracking));

        AtomicBoolean executed = new AtomicBoolean(false);
        assertThrows(ConflictException.class, () -> guard.validateAndExecute(42L, worker, () -> {
            executed.set(true);
            return "unexpected";
        }));

        assertTrue(!executed.get());
        verify(proximityValidator, never()).validate(any(), any(), any(), any(), any());
    }
}
