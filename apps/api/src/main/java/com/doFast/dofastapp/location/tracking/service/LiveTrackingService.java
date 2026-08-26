package com.doFast.dofastapp.location.tracking.service;

import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.entity.JobRouteStop;
import com.doFast.dofastapp.job.repository.JobRepository;
import com.doFast.dofastapp.location.routing.provider.RouteCoordinate;
import com.doFast.dofastapp.location.routing.provider.RouteProvider;
import com.doFast.dofastapp.location.routing.provider.RouteProviderResult;
import com.doFast.dofastapp.location.service.GeoPointFactory;
import com.doFast.dofastapp.location.tracking.dto.LiveLocationUpdateRequest;
import com.doFast.dofastapp.location.tracking.dto.LiveTrackingPointResponse;
import com.doFast.dofastapp.location.tracking.dto.LiveTrackingResponse;
import com.doFast.dofastapp.location.tracking.entity.JobLiveTracking;
import com.doFast.dofastapp.location.tracking.enums.TrackingPhase;
import com.doFast.dofastapp.location.tracking.repository.JobLiveTrackingRepository;
import com.doFast.dofastapp.user.entity.User;
import org.locationtech.jts.geom.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

@Service
public class LiveTrackingService {

    private static final Logger log = LoggerFactory.getLogger(LiveTrackingService.class);
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private final JobRepository jobRepository;
    private final JobLiveTrackingRepository trackingRepository;
    private final LiveTrackingAccessService accessService;
    private final TrackingPositionSanityValidator positionSanityValidator;
    private final TrackingUpdateRateLimiter updateRateLimiter;
    private final RouteProvider routeProvider;
    private final SimpMessagingTemplate messagingTemplate;
    private final TransactionTemplate transactionTemplate;
    private final long etaRefreshSeconds;
    private final double etaRefreshMovementMeters;
    private final long staleAfterSeconds;

    public LiveTrackingService(
            JobRepository jobRepository,
            JobLiveTrackingRepository trackingRepository,
            LiveTrackingAccessService accessService,
            TrackingPositionSanityValidator positionSanityValidator,
            TrackingUpdateRateLimiter updateRateLimiter,
            RouteProvider routeProvider,
            SimpMessagingTemplate messagingTemplate,
            PlatformTransactionManager transactionManager,
            @Value("${dofast.tracking.eta-refresh-seconds:30}") long etaRefreshSeconds,
            @Value("${dofast.tracking.eta-refresh-movement-meters:150}") double etaRefreshMovementMeters,
            @Value("${dofast.tracking.stale-after-seconds:20}") long staleAfterSeconds
    ) {
        this.jobRepository = jobRepository;
        this.trackingRepository = trackingRepository;
        this.accessService = accessService;
        this.positionSanityValidator = positionSanityValidator;
        this.updateRateLimiter = updateRateLimiter;
        this.routeProvider = routeProvider;
        this.messagingTemplate = messagingTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.etaRefreshSeconds = etaRefreshSeconds;
        this.etaRefreshMovementMeters = etaRefreshMovementMeters;
        this.staleAfterSeconds = staleAfterSeconds;
    }

    public void initializeForAcceptedJob(Job job) {
        LiveTrackingResponse response = transactionTemplate.execute(status -> {
            if (job.getId() == null || job.getTakenBy() == null) {
                throw new IllegalArgumentException("Przyjęte zlecenie musi mieć identyfikator i wykonawcę");
            }
            JobLiveTracking tracking = trackingRepository.findByJobIdForUpdate(job.getId())
                    .orElseGet(() -> JobLiveTracking.start(job.getId(), job.getTakenBy(), Instant.now()));
            return toResponse(trackingRepository.save(tracking), Instant.now());
        });
        broadcastAfterCommit(response);
    }

    public LiveTrackingResponse getTracking(Long jobId, User currentUser) {
        accessService.requireViewer(jobId, currentUser);
        JobLiveTracking tracking = trackingRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Śledzenie tego zlecenia nie zostało jeszcze uruchomione"));
        return toResponse(tracking, Instant.now());
    }

    public LiveTrackingResponse updateLocation(Long jobId, LiveLocationUpdateRequest request, User currentUser) {
        Instant now = Instant.now();
        validateCapturedAt(request.capturedAt(), now);

        PersistedPosition persisted = transactionTemplate.execute(status -> persistPosition(jobId, request, currentUser, now));
        Objects.requireNonNull(persisted, "Tracking transaction returned no result");

        LiveTrackingResponse response = persisted.response();
        if (persisted.refreshEstimate()) {
            response = refreshEstimate(persisted);
        }
        broadcast(response);
        return response;
    }

    public LiveTrackingResponse confirmPickup(Long jobId, User currentUser) {
        return confirmCheckpoint(jobId, currentUser);
    }

    public LiveTrackingResponse confirmCheckpoint(Long jobId, User currentUser) {
        Instant now = Instant.now();
        PersistedPosition persisted = transactionTemplate.execute(status -> {
            Job job = jobRepository.findByIdForUpdate(jobId)
                    .orElseThrow(() -> new ResourceNotFoundException("Zlecenie nie istnieje"));
            assertWorkerAndActive(job, currentUser);
            JobLiveTracking tracking = trackingRepository.findByJobIdForUpdate(jobId)
                    .orElseThrow(() -> new ResourceNotFoundException("Śledzenie tego zlecenia nie zostało uruchomione"));
            if (tracking.getPhase() == TrackingPhase.TO_DESTINATION) {
                return context(job, tracking, false, now);
            }
            advanceTarget(job, tracking, now);
            trackingRepository.save(tracking);
            return context(job, tracking, tracking.getCurrentLocation() != null, now);
        });
        Objects.requireNonNull(persisted, "Tracking transaction returned no result");

        LiveTrackingResponse response = persisted.refreshEstimate() ? refreshEstimate(persisted) : persisted.response();
        broadcast(response);
        return response;
    }

    public void stopAndClear(Long jobId) {
        LiveTrackingResponse response = transactionTemplate.execute(status -> trackingRepository.findByJobIdForUpdate(jobId)
                .map(tracking -> {
                    Instant now = Instant.now();
                    tracking.stopAndClear(now);
                    return toResponse(trackingRepository.save(tracking), now);
                })
                .orElse(null));
        if (response != null) {
            broadcastAfterCommit(response);
        }
    }

    private PersistedPosition persistPosition(
            Long jobId,
            LiveLocationUpdateRequest request,
            User currentUser,
            Instant now
    ) {
        Job job = jobRepository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Zlecenie nie istnieje"));
        assertWorkerAndActive(job, currentUser);

        JobLiveTracking tracking = trackingRepository.findByJobIdForUpdate(jobId)
                .orElseGet(() -> JobLiveTracking.start(jobId, job.getTakenBy(), now));
        if (tracking.getCapturedAt() != null && !request.capturedAt().isAfter(tracking.getCapturedAt())) {
            throw new ConflictException("Nowsza lokalizacja wykonawcy została już zapisana");
        }

        updateRateLimiter.validate(tracking.getReceivedAt(), now);
        positionSanityValidator.validate(
                tracking.getCurrentLocation(),
                tracking.getAccuracyMeters(),
                tracking.getCapturedAt(),
                request
        );

        Point current = GeoPointFactory.from(request.latitude(), request.longitude());
        boolean refreshEstimate = shouldRefreshEstimate(tracking, current, now);
        tracking.updatePosition(
                current,
                request.accuracyMeters(),
                request.headingDegrees(),
                request.speedMetersPerSecond(),
                request.capturedAt(),
                now
        );
        trackingRepository.save(tracking);
        return context(job, tracking, refreshEstimate, now);
    }

    private LiveTrackingResponse refreshEstimate(PersistedPosition persisted) {
        RouteProviderResult estimate;
        try {
            estimate = routeProvider.estimate(persisted.current(), persisted.target());
        } catch (RuntimeException ex) {
            log.warn("Could not refresh live ETA for job {}: {}", persisted.jobId(), ex.getMessage());
            return persisted.response();
        }

        LiveTrackingResponse applied = transactionTemplate.execute(status -> {
            Job job = jobRepository.findByIdForUpdate(persisted.jobId())
                    .orElseThrow(() -> new ResourceNotFoundException("Zlecenie nie istnieje"));
            JobLiveTracking tracking = trackingRepository.findByJobIdForUpdate(persisted.jobId())
                    .orElseThrow(() -> new ResourceNotFoundException("Śledzenie tego zlecenia nie zostało uruchomione"));

            if (!LiveTrackingAccessService.isTrackingActive(job)) {
                tracking.stopAndClear(Instant.now());
                return toResponse(trackingRepository.save(tracking), Instant.now());
            }
            if (tracking.getPhase() != persisted.phase()
                    || !Objects.equals(tracking.getNextStopSequence(), persisted.nextStopSequence())
                    || tracking.getCapturedAt() == null
                    || !tracking.getCapturedAt().equals(persisted.capturedAt())) {
                return toResponse(tracking, Instant.now());
            }

            Point etaOrigin = GeoPointFactory.from(
                    java.math.BigDecimal.valueOf(persisted.current().latitude()),
                    java.math.BigDecimal.valueOf(persisted.current().longitude())
            );
            tracking.applyEstimate(estimate, etaOrigin, Instant.now());
            return toResponse(trackingRepository.save(tracking), Instant.now());
        });
        return applied != null ? applied : persisted.response();
    }

    private PersistedPosition context(Job job, JobLiveTracking tracking, boolean refreshEstimate, Instant now) {
        Point currentPoint = tracking.getCurrentLocation();
        Point targetPoint = switch (tracking.getPhase()) {
            case TO_ORIGIN -> job.getLocation();
            case TO_STOP -> routeStop(job, requiredStopSequence(tracking)).getLocation();
            case TO_DESTINATION -> job.getDestinationLocation();
        };
        RouteCoordinate current = currentPoint == null ? null : coordinate(currentPoint);
        RouteCoordinate target = targetPoint == null ? null : coordinate(targetPoint);
        boolean canRefresh = refreshEstimate && current != null && target != null && tracking.getCapturedAt() != null;
        return new PersistedPosition(
                job.getId(),
                tracking.getPhase(),
                tracking.getNextStopSequence(),
                tracking.getCapturedAt(),
                current,
                target,
                canRefresh,
                toResponse(tracking, now)
        );
    }

    private void advanceTarget(Job job, JobLiveTracking tracking, Instant now) {
        if (tracking.getPhase() == TrackingPhase.TO_ORIGIN) {
            if (job.getRouteStops().isEmpty()) {
                tracking.switchToDestination(now);
            } else {
                tracking.switchToStop(job.getRouteStops().getFirst().getSequenceNo());
            }
            return;
        }
        if (tracking.getPhase() == TrackingPhase.TO_STOP) {
            int currentSequence = requiredStopSequence(tracking);
            JobRouteStop nextStop = job.getRouteStops().stream()
                    .filter(stop -> stop.getSequenceNo() > currentSequence)
                    .findFirst()
                    .orElse(null);
            if (nextStop == null) {
                tracking.switchToDestination(now);
            } else {
                tracking.switchToStop(nextStop.getSequenceNo());
            }
        }
    }

    private int requiredStopSequence(JobLiveTracking tracking) {
        if (tracking.getNextStopSequence() == null) {
            throw new ConflictException("Stan śledzenia przystanków jest nieprawidłowy");
        }
        return tracking.getNextStopSequence();
    }

    private JobRouteStop routeStop(Job job, int sequenceNo) {
        return job.getRouteStops().stream()
                .filter(stop -> stop.getSequenceNo() == sequenceNo)
                .findFirst()
                .orElseThrow(() -> new ConflictException("Przystanek trasy nie istnieje"));
    }

    private boolean shouldRefreshEstimate(JobLiveTracking tracking, Point current, Instant now) {
        if (tracking.getRemainingComputedAt() == null || tracking.getEtaOriginLocation() == null) {
            return true;
        }
        if (Duration.between(tracking.getRemainingComputedAt(), now).getSeconds() >= etaRefreshSeconds) {
            return true;
        }
        return haversineMeters(coordinate(tracking.getEtaOriginLocation()), coordinate(current)) >= etaRefreshMovementMeters;
    }

    private void validateCapturedAt(Instant capturedAt, Instant now) {
        if (capturedAt.isAfter(now.plus(Duration.ofMinutes(10)))) {
            throw new ConflictException("Czas aktualizacji lokalizacji jest nieprawidłowy");
        }
        if (capturedAt.isBefore(now.minus(Duration.ofMinutes(10)))) {
            throw new ConflictException("Aktualizacja lokalizacji jest zbyt stara");
        }
    }

    private void assertWorkerAndActive(Job job, User user) {
        if (!LiveTrackingAccessService.isTrackingActive(job)) {
            throw new ConflictException("Śledzenie lokalizacji nie jest aktywne dla tego zlecenia");
        }
        if (!sameUser(job.getTakenBy(), user)) {
            throw new ForbiddenOperationException("Tylko przypisany wykonawca może udostępniać lokalizację");
        }
    }

    private LiveTrackingResponse toResponse(JobLiveTracking tracking, Instant now) {
        boolean active = tracking.getCurrentLocation() != null && tracking.getSharingStoppedAt() == null;
        LiveTrackingPointResponse point = tracking.getCurrentLocation() == null ? null : new LiveTrackingPointResponse(
                tracking.getCurrentLocation().getY(),
                tracking.getCurrentLocation().getX(),
                tracking.getAccuracyMeters(),
                tracking.getHeadingDegrees(),
                tracking.getSpeedMetersPerSecond(),
                tracking.getCapturedAt()
        );
        boolean stale = active && (tracking.getReceivedAt() == null
                || Duration.between(tracking.getReceivedAt(), now).getSeconds() > staleAfterSeconds);
        return new LiveTrackingResponse(
                tracking.getJobId(),
                tracking.getWorker().getId(),
                tracking.getPhase(),
                tracking.getNextStopSequence(),
                active,
                point,
                tracking.getRemainingDistanceMeters(),
                tracking.getRemainingDurationSeconds(),
                tracking.getRemainingEncodedPolyline(),
                tracking.getRemainingProvider(),
                tracking.getRemainingComputedAt(),
                tracking.getReceivedAt(),
                stale
        );
    }

    private RouteCoordinate coordinate(Point point) {
        return new RouteCoordinate(point.getY(), point.getX());
    }

    private double haversineMeters(RouteCoordinate first, RouteCoordinate second) {
        double lat1 = Math.toRadians(first.latitude());
        double lat2 = Math.toRadians(second.latitude());
        double deltaLat = Math.toRadians(second.latitude() - first.latitude());
        double deltaLon = Math.toRadians(second.longitude() - first.longitude());
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        return EARTH_RADIUS_METERS * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private boolean sameUser(User first, User second) {
        return first != null && second != null && first.getId() != null && first.getId().equals(second.getId());
    }

    private void broadcast(LiveTrackingResponse response) {
        messagingTemplate.convertAndSend("/topic/tracking/" + response.jobId(), response);
    }

    private void broadcastAfterCommit(LiveTrackingResponse response) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    broadcast(response);
                }
            });
        } else {
            broadcast(response);
        }
    }

    private record PersistedPosition(
            Long jobId,
            TrackingPhase phase,
            Integer nextStopSequence,
            Instant capturedAt,
            RouteCoordinate current,
            RouteCoordinate target,
            boolean refreshEstimate,
            LiveTrackingResponse response
    ) {}
}
