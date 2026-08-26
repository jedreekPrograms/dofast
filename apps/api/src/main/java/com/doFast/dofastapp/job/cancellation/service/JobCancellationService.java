package com.doFast.dofastapp.job.cancellation.service;

import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
import com.doFast.dofastapp.job.cancellation.dto.CreateJobCancellationRequest;
import com.doFast.dofastapp.job.cancellation.dto.JobCancellationResponse;
import com.doFast.dofastapp.job.cancellation.entity.JobCancellationRequest;
import com.doFast.dofastapp.job.cancellation.enums.JobCancellationStatus;
import com.doFast.dofastapp.job.cancellation.repository.JobCancellationRequestRepository;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.repository.JobRepository;
import com.doFast.dofastapp.location.tracking.service.LiveTrackingService;
import com.doFast.dofastapp.notification.enums.NotificationType;
import com.doFast.dofastapp.notification.service.NotificationService;
import com.doFast.dofastapp.payment.service.TransactionService;
import com.doFast.dofastapp.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class JobCancellationService {

    private final JobRepository jobRepository;
    private final JobCancellationRequestRepository cancellationRepository;
    private final TransactionService transactionService;
    private final LiveTrackingService liveTrackingService;
    private final NotificationService notificationService;

    public JobCancellationService(
            JobRepository jobRepository,
            JobCancellationRequestRepository cancellationRepository,
            TransactionService transactionService,
            LiveTrackingService liveTrackingService,
            NotificationService notificationService
    ) {
        this.jobRepository = jobRepository;
        this.cancellationRepository = cancellationRepository;
        this.transactionService = transactionService;
        this.liveTrackingService = liveTrackingService;
        this.notificationService = notificationService;
    }

    public Optional<JobCancellationResponse> getPending(Long jobId, User currentUser) {
        Job job = getJob(jobId);
        assertParticipant(job, currentUser);
        return cancellationRepository
                .findFirstByJob_IdAndStatusOrderByRequestedAtDesc(jobId, JobCancellationStatus.PENDING)
                .map(request -> toResponse(request, job));
    }

    @Transactional
    public JobCancellationResponse requestCancellation(
            Long jobId,
            CreateJobCancellationRequest payload,
            User currentUser
    ) {
        Job job = getJobForUpdate(jobId);
        assertCanNegotiateCancellation(job, currentUser);

        if (cancellationRepository.findPendingForUpdate(jobId, JobCancellationStatus.PENDING).isPresent()) {
            throw new ConflictException("Dla tego zlecenia istnieje już oczekująca prośba o anulowanie");
        }

        String reason = payload.reason().trim();
        JobCancellationRequest request = JobCancellationRequest.pending(job, currentUser, reason, LocalDateTime.now());
        JobCancellationRequest saved = cancellationRepository.save(request);
        User counterparty = counterparty(job, currentUser);

        notificationService.notify(
                counterparty,
                NotificationType.JOB_CANCELLATION_REQUESTED,
                "Prośba o anulowanie zlecenia",
                currentUser.getNickname() + " prosi o anulowanie zlecenia „" + job.getTitle() + "”.",
                job,
                null
        );
        return toResponse(saved, job);
    }

    @Transactional
    public JobCancellationResponse approve(Long jobId, User currentUser) {
        Job job = getJobForUpdate(jobId);
        assertCanNegotiateCancellation(job, currentUser);
        JobCancellationRequest request = getPendingForUpdate(jobId);
        assertCounterpartyCanResolve(request, job, currentUser);

        LocalDateTime now = LocalDateTime.now();
        request.approve(currentUser, now);
        job.cancel(now);
        cancellationRepository.save(request);
        jobRepository.save(job);

        // V12 clears tracking through a DB trigger when a job becomes CANCELLED and increments
        // the tracking optimistic-lock version. Flush that transition before the application-side
        // clear so LiveTrackingService reloads the current version and can publish the stopped state.
        jobRepository.flush();
        liveTrackingService.stopAndClear(jobId);
        transactionService.refundMoney(job);

        notificationService.notify(
                request.getRequestedBy(),
                NotificationType.JOB_CANCELLED,
                "Zlecenie anulowane",
                "Druga strona zaakceptowała anulowanie zlecenia „" + job.getTitle() + "”. Środki escrow zostały zwrócone.",
                job,
                null
        );
        return toResponse(request, job);
    }

    @Transactional
    public JobCancellationResponse decline(Long jobId, User currentUser) {
        Job job = getJobForUpdate(jobId);
        assertCanNegotiateCancellation(job, currentUser);
        JobCancellationRequest request = getPendingForUpdate(jobId);
        assertCounterpartyCanResolve(request, job, currentUser);

        request.decline(currentUser, LocalDateTime.now());
        JobCancellationRequest saved = cancellationRepository.save(request);
        notificationService.notify(
                request.getRequestedBy(),
                NotificationType.JOB_CANCELLATION_DECLINED,
                "Anulowanie odrzucone",
                "Druga strona odrzuciła prośbę o anulowanie zlecenia „" + job.getTitle() + "”.",
                job,
                null
        );
        return toResponse(saved, job);
    }

    @Transactional
    public JobCancellationResponse withdraw(Long jobId, User currentUser) {
        Job job = getJobForUpdate(jobId);
        assertCanNegotiateCancellation(job, currentUser);
        JobCancellationRequest request = getPendingForUpdate(jobId);
        if (!sameUser(request.getRequestedBy(), currentUser)) {
            throw new ForbiddenOperationException("Tylko autor prośby może ją wycofać");
        }

        request.withdraw(currentUser, LocalDateTime.now());
        JobCancellationRequest saved = cancellationRepository.save(request);
        User counterparty = counterparty(job, currentUser);
        notificationService.notify(
                counterparty,
                NotificationType.JOB_CANCELLATION_WITHDRAWN,
                "Prośba o anulowanie wycofana",
                currentUser.getNickname() + " wycofał prośbę o anulowanie zlecenia „" + job.getTitle() + "”.",
                job,
                null
        );
        return toResponse(saved, job);
    }

    private Job getJob(Long jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Zlecenie nie istnieje"));
    }

    private Job getJobForUpdate(Long jobId) {
        return jobRepository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Zlecenie nie istnieje"));
    }

    private JobCancellationRequest getPendingForUpdate(Long jobId) {
        return cancellationRepository.findPendingForUpdate(jobId, JobCancellationStatus.PENDING)
                .orElseThrow(() -> new ResourceNotFoundException("Brak oczekującej prośby o anulowanie"));
    }

    private void assertCanNegotiateCancellation(Job job, User currentUser) {
        assertParticipant(job, currentUser);
        if (job.getStatus() != JobStatus.IN_PROGRESS || job.getTakenBy() == null) {
            throw new ConflictException("Anulowanie za zgodą stron jest dostępne tylko podczas aktywnej realizacji");
        }
    }

    private void assertParticipant(Job job, User currentUser) {
        if (!sameUser(job.getCreatedBy(), currentUser) && !sameUser(job.getTakenBy(), currentUser)) {
            throw new ForbiddenOperationException("Tylko uczestnicy zlecenia mogą zarządzać jego anulowaniem");
        }
    }

    private void assertCounterpartyCanResolve(JobCancellationRequest request, Job job, User currentUser) {
        if (sameUser(request.getRequestedBy(), currentUser)) {
            throw new ForbiddenOperationException("Autor prośby nie może sam jej zaakceptować ani odrzucić");
        }
        if (!sameUser(counterparty(job, request.getRequestedBy()), currentUser)) {
            throw new ForbiddenOperationException("Tylko druga strona zlecenia może odpowiedzieć na prośbę o anulowanie");
        }
    }

    private User counterparty(Job job, User user) {
        if (sameUser(job.getCreatedBy(), user)) {
            if (job.getTakenBy() == null) {
                throw new ConflictException("Zlecenie nie ma przypisanego wykonawcy");
            }
            return job.getTakenBy();
        }
        if (sameUser(job.getTakenBy(), user)) {
            return job.getCreatedBy();
        }
        throw new ForbiddenOperationException("Użytkownik nie jest uczestnikiem zlecenia");
    }

    private boolean sameUser(User first, User second) {
        return first != null && second != null && first.getId() != null && first.getId().equals(second.getId());
    }

    private JobCancellationResponse toResponse(JobCancellationRequest request, Job job) {
        User counterparty = counterparty(job, request.getRequestedBy());
        return new JobCancellationResponse(
                request.getId(),
                job.getId(),
                request.getRequestedBy().getId(),
                counterparty.getId(),
                request.getReason(),
                request.getStatus(),
                request.getRequestedAt(),
                request.getResolvedAt(),
                request.getResolvedBy() != null ? request.getResolvedBy().getId() : null
        );
    }
}
