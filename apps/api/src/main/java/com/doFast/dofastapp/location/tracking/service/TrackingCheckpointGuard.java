package com.doFast.dofastapp.location.tracking.service;

import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
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
        Job job = jobRepository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Zlecenie nie istnieje"));
        if (!LiveTrackingAccessService.isTrackingActive(job)) {
            throw new ConflictException("Śledzenie lokalizacji nie jest aktywne dla tego zlecenia");
        }
        if (!sameUser(job.getTakenBy(), currentUser)) {
            throw new ForbiddenOperationException("Tylko przypisany wykonawca może potwierdzić punkt trasy");
        }

        JobLiveTracking tracking = trackingRepository.findByJobIdForUpdate(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Śledzenie tego zlecenia nie zostało jeszcze uruchomione"));
        if (tracking.getPhase() != TrackingPhase.TO_DESTINATION) {
            proximityValidator.validate(
                    tracking.getCurrentLocation(),
                    tracking.getAccuracyMeters(),
                    tracking.getReceivedAt(),
                    targetLocation(job, tracking),
                    Instant.now()
            );
        }

        return action.get();
    }

    private Point targetLocation(Job job, JobLiveTracking tracking) {
        if (tracking.getPhase() == TrackingPhase.TO_ORIGIN) {
            return job.getLocation();
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

    private boolean sameUser(User first, User second) {
        return first != null && second != null && first.getId() != null && first.getId().equals(second.getId());
    }
}
