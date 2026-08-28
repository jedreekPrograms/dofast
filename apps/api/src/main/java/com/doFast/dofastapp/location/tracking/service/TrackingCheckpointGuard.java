package com.doFast.dofastapp.location.tracking.service;

import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.entity.JobRouteStop;
import com.doFast.dofastapp.location.tracking.entity.JobLiveTracking;
import com.doFast.dofastapp.location.tracking.enums.TrackingPhase;
import com.doFast.dofastapp.user.entity.User;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class TrackingCheckpointGuard {

    private final TrackingCheckpointProximityValidator proximityValidator;

    public TrackingCheckpointGuard(TrackingCheckpointProximityValidator proximityValidator) {
        this.proximityValidator = proximityValidator;
    }

    public void validate(Job job, JobLiveTracking tracking, User currentUser, Instant now) {
        if (!LiveTrackingAccessService.isTrackingActive(job)) {
            throw new ConflictException("Śledzenie lokalizacji nie jest aktywne dla tego zlecenia");
        }
        if (!sameUser(job.getTakenBy(), currentUser)) {
            throw new ForbiddenOperationException("Tylko przypisany wykonawca może potwierdzić punkt trasy");
        }
        if (tracking.getPhase() == TrackingPhase.TO_DESTINATION) {
            return;
        }

        proximityValidator.validate(
                tracking.getCurrentLocation(),
                tracking.getAccuracyMeters(),
                tracking.getReceivedAt(),
                targetLocation(job, tracking),
                now
        );
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
