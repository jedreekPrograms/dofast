package com.doFast.dofastapp.location.tracking.service;

import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.entity.JobRouteStop;
import com.doFast.dofastapp.job.repository.JobRepository;
import com.doFast.dofastapp.location.tracking.entity.JobLiveTracking;
import com.doFast.dofastapp.location.tracking.enums.TrackingPhase;
import com.doFast.dofastapp.location.tracking.repository.JobLiveTrackingRepository;
import com.doFast.dofastapp.user.entity.User;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.function.Supplier;

@Service
public class TrackingCheckpointGuard {

    private static final String JOB_NOT_FOUND = "Zlecenie nie istnieje";

    private final JobRepository jobRepository;
    private final JobLiveTrackingRepository trackingRepository;
    private final TrackingCheckpointProximityValidator proximityValidator;

    public TrackingCheckpointGuard(
            JobRepository jobRepository,
            JobLiveTrackingRepository trackingRepository,
            TrackingCheckpointProximityValidator proximityValidator
    ) {
        this.jobRepository = jobRepository;
        this.trackingRepository = trackingRepository;
        this.proximityValidator = proximityValidator;
    }

    @Transactional
    public <T> T validateAndExecute(Long jobId, User currentUser, Supplier<T> action) {
        Long workerId = requireActorId(currentUser);
        Job job = jobRepository.findAssignedWorkerByIdForUpdate(jobId, workerId)
                .orElseThrow(() -> new ResourceNotFoundException(JOB_NOT_FOUND));
        if (!LiveTrackingAccessService.isTrackingActive(job)) {
            throw new ConflictException("Śledzenie lokalizacji nie jest aktywne dla tego zlecenia");
        }

        JobLiveTracking tracking = trackingRepository.findByJobIdForUpdate(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Śledzenie tego zlecenia nie zostało jeszcze uruchomione"));
        if (tracking.getPhase() == TrackingPhase.ARRIVED_DESTINATION) {
            throw new ConflictException("Dotarcie do miejsca docelowego zostało już potwierdzone");
        }

        proximityValidator.validate(
                tracking.getCurrentLocation(),
                tracking.getAccuracyMeters(),
                tracking.getReceivedAt(),
                targetLocation(job, tracking),
                Instant.now()
        );

        return action.get();
    }

    private Long requireActorId(User currentUser) {
        if (currentUser == null || currentUser.getId() == null) {
            throw new ResourceNotFoundException(JOB_NOT_FOUND);
        }
        return currentUser.getId();
    }

    private Point targetLocation(Job job, JobLiveTracking tracking) {
        if (tracking.getPhase() == TrackingPhase.TO_ORIGIN) {
            return job.getLocation();
        }
        if (tracking.getPhase() == TrackingPhase.TO_DESTINATION) {
            return job.getDestinationLocation();
        }
        Integer sequence = tracking.getNextStopSequence();
        if (sequence == null) {
            throw new ConflictException("Stan śledzenia przystanków jest nieprawidłowy");
        }
        return job.getRouteStops().stream()
                .filter(stop -> stop.getSequenceNo() == sequence)
                .map(JobRouteStop::getLocation)
                .findFirst()
                .orElseThrow(() -> new ConflictException("Przystanek trasy nie istnieje"));
    }
}
