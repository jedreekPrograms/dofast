package com.doFast.dofastapp.job.service;

import com.doFast.dofastapp.common.dto.PageResponse;
import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.common.exception.BusinessException;
import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
import com.doFast.dofastapp.job.dto.JobRequest;
import com.doFast.dofastapp.job.dto.JobResponse;
import com.doFast.dofastapp.job.dto.NearbyJobResponse;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.repository.JobRepository;
import com.doFast.dofastapp.location.dto.LocationResponse;
import com.doFast.dofastapp.location.service.GeoPointFactory;
import com.doFast.dofastapp.notification.enums.NotificationType;
import com.doFast.dofastapp.notification.service.NotificationService;
import com.doFast.dofastapp.payment.service.TransactionService;
import com.doFast.dofastapp.user.entity.User;
import org.locationtech.jts.geom.Point;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class JobService {

    private final JobRepository jobRepository;
    private final TransactionService transactionService;
    private final NotificationService notificationService;

    public JobService(
            JobRepository jobRepository,
            TransactionService transactionService,
            NotificationService notificationService
    ) {
        this.jobRepository = jobRepository;
        this.transactionService = transactionService;
        this.notificationService = notificationService;
    }

    @Transactional
    public JobResponse createJob(JobRequest request, User user) {
        Job job = new Job();
        job.setTitle(request.getTitle().trim());
        job.setDescription(request.getDescription().trim());
        job.setPrice(request.getPrice());
        job.setStatus(JobStatus.OPEN);
        job.setLocation(GeoPointFactory.from(request.getLocation()));
        job.setLocationLabel(request.getLocation().getPublicLabel().trim());
        job.setLocationPrivateLabel(normalizeOptionalLabel(request.getLocation().getPrivateLabel()));
        job.setCreatedBy(user);

        Job saved = jobRepository.save(job);
        transactionService.holdMoney(saved);

        return toResponse(saved);
    }

    public PageResponse<JobResponse> getOpenJobs(
            String query,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            int page,
            int size
    ) {
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new BusinessException("Minimalna cena nie może być większa od maksymalnej");
        }

        String normalizedQuery = normalizeOptionalLabel(query);
        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );

        Page<Job> result = jobRepository.findOpenJobs(
                JobStatus.OPEN,
                normalizedQuery,
                minPrice,
                maxPrice,
                pageable
        );

        List<JobResponse> content = result.getContent()
                .stream()
                .map(this::toResponse)
                .toList();

        return PageResponse.from(result, content);
    }

    public List<NearbyJobResponse> getNearbyJobs(
            double latitude,
            double longitude,
            int radiusMeters,
            int limit
    ) {
        return jobRepository.findNearbyOpenJobs(latitude, longitude, radiusMeters, limit)
                .stream()
                .map(match -> new NearbyJobResponse(
                        match.getId(),
                        match.getTitle(),
                        match.getDescription(),
                        match.getPrice(),
                        JobStatus.valueOf(match.getStatus()),
                        match.getLocationLabel(),
                        Math.round(match.getDistanceMeters()),
                        match.getCreatedAt()
                ))
                .toList();
    }

    public JobResponse getJob(Long jobId) {
        return toResponse(getJobForRead(jobId));
    }

    public LocationResponse getExactLocation(Long jobId, User currentUser) {
        Job job = getJobForRead(jobId);

        if (!canAccessExactLocation(job, currentUser)) {
            throw new ForbiddenOperationException("Dokładna lokalizacja jest dostępna tylko dla stron aktywnego zlecenia");
        }

        Point point = job.getLocation();
        if (point == null) {
            throw new ResourceNotFoundException("Dokładna lokalizacja zlecenia nie jest dostępna");
        }

        String exactLabel = job.getLocationPrivateLabel() != null
                ? job.getLocationPrivateLabel()
                : job.getLocationLabel();

        return new LocationResponse(
                point.getY(),
                point.getX(),
                exactLabel
        );
    }

    @Transactional
    public JobResponse acceptJob(Long jobId, User currentUser) {
        Job job = getJobForUpdate(jobId);

        if (job.getStatus() != JobStatus.OPEN) {
            throw new ConflictException("Zlecenie nie jest już dostępne");
        }

        if (sameUser(job.getCreatedBy(), currentUser)) {
            throw new ForbiddenOperationException("Nie możesz przyjąć własnego zlecenia");
        }

        job.assignTo(currentUser, LocalDateTime.now());
        Job saved = jobRepository.save(job);
        notificationService.notify(
                saved.getCreatedBy(),
                NotificationType.JOB_ACCEPTED,
                "Zlecenie zostało przyjęte",
                currentUser.getNickname() + " przyjął zlecenie „" + saved.getTitle() + "”",
                saved,
                null
        );
        return toResponse(saved);
    }

    public List<JobResponse> getMyJobs(User user) {
        return jobRepository.findByCreatedByOrTakenByOrderByCreatedAtDesc(user, user)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public JobResponse requestCompletion(Long jobId, User currentUser) {
        Job job = getJobForUpdate(jobId);

        if (job.getStatus() != JobStatus.IN_PROGRESS) {
            throw new ConflictException("Zlecenie nie jest w trakcie realizacji");
        }

        if (job.getTakenBy() == null || !sameUser(job.getTakenBy(), currentUser)) {
            throw new ForbiddenOperationException("Tylko wykonawca może zgłosić wykonanie zlecenia");
        }

        job.requestCompletion(LocalDateTime.now());
        Job saved = jobRepository.save(job);
        notificationService.notify(
                saved.getCreatedBy(),
                NotificationType.COMPLETION_REQUESTED,
                "Wykonawca zgłosił zakończenie",
                "Potwierdź wykonanie zlecenia „" + saved.getTitle() + "” albo otwórz spór.",
                saved,
                null
        );
        return toResponse(saved);
    }

    @Transactional
    public JobResponse confirmCompletion(Long jobId, User currentUser) {
        Job job = getJobForUpdate(jobId);

        if (!sameUser(job.getCreatedBy(), currentUser)) {
            throw new ForbiddenOperationException("Tylko autor może potwierdzić wykonanie zlecenia");
        }

        if (job.getStatus() != JobStatus.COMPLETION_REQUESTED) {
            throw new ConflictException("Wykonawca nie zgłosił jeszcze wykonania zlecenia");
        }

        if (job.getTakenBy() == null) {
            throw new ConflictException("Zlecenie nie ma przypisanego wykonawcy");
        }

        job.complete(LocalDateTime.now());
        Job saved = jobRepository.save(job);
        transactionService.releaseMoney(saved, saved.getTakenBy());
        notificationService.notify(
                saved.getTakenBy(),
                NotificationType.JOB_COMPLETED,
                "Zlecenie potwierdzone",
                "Zlecenie „" + saved.getTitle() + "” zostało zakończone, a środki zwolnione.",
                saved,
                null
        );

        return toResponse(saved);
    }

    @Transactional
    public JobResponse cancelJob(Long jobId, User currentUser) {
        Job job = getJobForUpdate(jobId);

        if (!sameUser(job.getCreatedBy(), currentUser)) {
            throw new ForbiddenOperationException("Tylko autor może anulować zlecenie");
        }

        if (job.getStatus() != JobStatus.OPEN) {
            throw new ConflictException("Można anulować tylko zlecenie, którego nikt jeszcze nie przyjął");
        }

        job.cancel(LocalDateTime.now());
        Job saved = jobRepository.save(job);
        transactionService.refundMoney(saved);

        return toResponse(saved);
    }

    private Job getJobForRead(Long jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Zlecenie nie istnieje"));
    }

    private Job getJobForUpdate(Long jobId) {
        return jobRepository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Zlecenie nie istnieje"));
    }

    private boolean canAccessExactLocation(Job job, User currentUser) {
        if (sameUser(job.getCreatedBy(), currentUser)) {
            return true;
        }

        boolean assignedWorker = sameUser(job.getTakenBy(), currentUser);
        boolean activeJob = job.getStatus() == JobStatus.IN_PROGRESS
                || job.getStatus() == JobStatus.COMPLETION_REQUESTED
                || job.getStatus() == JobStatus.DISPUTED;

        return assignedWorker && activeJob;
    }

    private boolean sameUser(User first, User second) {
        return first != null
                && second != null
                && first.getId() != null
                && first.getId().equals(second.getId());
    }

    private String normalizeOptionalLabel(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private JobResponse toResponse(Job job) {
        return new JobResponse(
                job.getId(),
                job.getTitle(),
                job.getDescription(),
                job.getPrice(),
                job.getStatus(),
                job.getLocationLabel(),
                job.getCreatedBy().getId(),
                job.getTakenBy() != null ? job.getTakenBy().getId() : null,
                job.getCreatedAt(),
                job.getUpdatedAt(),
                job.getTakenAt(),
                job.getCompletionRequestedAt(),
                job.getCompletedAt(),
                job.getCancelledAt()
        );
    }
}
