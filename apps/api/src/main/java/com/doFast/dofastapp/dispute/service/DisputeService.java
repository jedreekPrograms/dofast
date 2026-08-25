package com.doFast.dofastapp.dispute.service;

import com.doFast.dofastapp.common.dto.PageResponse;
import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
import com.doFast.dofastapp.dispute.dto.CreateDisputeRequest;
import com.doFast.dofastapp.dispute.dto.DisputeDetailResponse;
import com.doFast.dofastapp.dispute.dto.DisputeEventResponse;
import com.doFast.dofastapp.dispute.dto.DisputeResponse;
import com.doFast.dofastapp.dispute.dto.ResolveDisputeRequest;
import com.doFast.dofastapp.dispute.entity.Dispute;
import com.doFast.dofastapp.dispute.entity.DisputeEvent;
import com.doFast.dofastapp.dispute.enums.DisputeEventType;
import com.doFast.dofastapp.dispute.enums.DisputeResolution;
import com.doFast.dofastapp.dispute.enums.DisputeStatus;
import com.doFast.dofastapp.dispute.repository.DisputeEventRepository;
import com.doFast.dofastapp.dispute.repository.DisputeRepository;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.repository.JobRepository;
import com.doFast.dofastapp.notification.enums.NotificationType;
import com.doFast.dofastapp.notification.service.NotificationService;
import com.doFast.dofastapp.payment.service.TransactionService;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.enums.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class DisputeService {

    private static final EnumSet<DisputeStatus> ACTIVE_STATUSES =
            EnumSet.of(DisputeStatus.OPEN, DisputeStatus.UNDER_REVIEW);

    private final DisputeRepository disputeRepository;
    private final DisputeEventRepository eventRepository;
    private final JobRepository jobRepository;
    private final TransactionService transactionService;
    private final NotificationService notificationService;

    public DisputeService(
            DisputeRepository disputeRepository,
            DisputeEventRepository eventRepository,
            JobRepository jobRepository,
            TransactionService transactionService,
            NotificationService notificationService
    ) {
        this.disputeRepository = disputeRepository;
        this.eventRepository = eventRepository;
        this.jobRepository = jobRepository;
        this.transactionService = transactionService;
        this.notificationService = notificationService;
    }

    @Transactional
    public DisputeDetailResponse openDispute(CreateDisputeRequest request, User currentUser) {
        Job job = jobRepository.findByIdForUpdate(request.jobId())
                .orElseThrow(() -> new ResourceNotFoundException("Zlecenie nie istnieje"));

        assertParticipant(job, currentUser);

        if (job.getStatus() != JobStatus.IN_PROGRESS
                && job.getStatus() != JobStatus.COMPLETION_REQUESTED) {
            throw new ConflictException("Spór można otworzyć tylko dla aktywnego, przyjętego zlecenia");
        }

        disputeRepository.findFirstByJobAndStatusInOrderByOpenedAtDesc(job, ACTIVE_STATUSES)
                .ifPresent(existing -> {
                    throw new ConflictException("Dla tego zlecenia istnieje już aktywny spór");
                });

        transactionService.assertHeld(job);

        LocalDateTime now = LocalDateTime.now();
        JobStatus previousStatus = job.getStatus();
        job.markDisputed();
        jobRepository.save(job);

        Dispute dispute = new Dispute();
        dispute.setJob(job);
        dispute.setOpenedBy(currentUser);
        dispute.setReason(request.reason());
        dispute.setDescription(request.description().trim());
        dispute.setStatus(DisputeStatus.OPEN);
        dispute.setPreviousJobStatus(previousStatus);
        dispute.setOpenedAt(now);

        Dispute saved = disputeRepository.save(dispute);
        recordEvent(saved, currentUser, DisputeEventType.OPENED, request.description().trim(), now);

        User other = otherParticipant(job, currentUser);
        notificationService.notify(
                other,
                NotificationType.DISPUTE_OPENED,
                "Otwarto spór",
                currentUser.getNickname() + " otworzył spór dotyczący zlecenia „" + job.getTitle() + "”",
                job,
                saved
        );

        return toDetail(saved);
    }

    public List<DisputeResponse> getMyDisputes(User currentUser) {
        return disputeRepository.findAllForParticipant(currentUser)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public DisputeDetailResponse getDispute(Long disputeId, User currentUser) {
        Dispute dispute = getForRead(disputeId);
        assertParticipantOrAdmin(dispute, currentUser);
        return toDetail(dispute);
    }

    @Transactional
    public DisputeDetailResponse cancelDispute(Long disputeId, User currentUser) {
        Dispute dispute = getForUpdate(disputeId);

        if (!sameUser(dispute.getOpenedBy(), currentUser)) {
            throw new ForbiddenOperationException("Tylko osoba, która otworzyła spór, może go anulować");
        }
        if (dispute.getStatus() != DisputeStatus.OPEN) {
            throw new ConflictException("Można anulować tylko spór, którego admin jeszcze nie podjął");
        }

        Job job = jobRepository.findByIdForUpdate(dispute.getJob().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Zlecenie nie istnieje"));
        assertJobIsDisputed(job);
        transactionService.assertHeld(job);

        LocalDateTime now = LocalDateTime.now();
        job.restoreAfterDispute(dispute.getPreviousJobStatus());
        jobRepository.save(job);
        dispute.cancel(now);
        Dispute saved = disputeRepository.save(dispute);
        recordEvent(saved, currentUser, DisputeEventType.CANCELLED, "Spór anulowany przez zgłaszającego", now);

        User other = otherParticipant(job, currentUser);
        notificationService.notify(
                other,
                NotificationType.DISPUTE_RESOLVED,
                "Spór anulowany",
                "Spór dotyczący zlecenia „" + job.getTitle() + "” został anulowany przez zgłaszającego.",
                job,
                saved
        );

        return toDetail(saved);
    }

    public PageResponse<DisputeResponse> getAdminDisputes(
            DisputeStatus status,
            int page,
            int size
    ) {
        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.asc("openedAt"), Sort.Order.asc("id"))
        );

        Page<Dispute> result = status == null
                ? disputeRepository.findAll(pageable)
                : disputeRepository.findByStatus(status, pageable);

        List<DisputeResponse> content = result.getContent().stream()
                .map(this::toResponse)
                .toList();
        return PageResponse.from(result, content);
    }

    public DisputeDetailResponse getAdminDispute(Long disputeId, User admin) {
        assertAdmin(admin);
        return toDetail(getForRead(disputeId));
    }

    @Transactional
    public DisputeDetailResponse claimDispute(Long disputeId, User admin) {
        assertAdmin(admin);
        Dispute dispute = getForUpdate(disputeId);

        if (dispute.getStatus() == DisputeStatus.RESOLVED || dispute.getStatus() == DisputeStatus.CANCELLED) {
            throw new ConflictException("Ten spór jest już zamknięty");
        }

        if (dispute.getAssignedAdmin() != null && !sameUser(dispute.getAssignedAdmin(), admin)) {
            throw new ConflictException("Spór jest już przypisany do innego administratora");
        }

        if (dispute.getStatus() == DisputeStatus.UNDER_REVIEW && sameUser(dispute.getAssignedAdmin(), admin)) {
            return toDetail(dispute);
        }

        LocalDateTime now = LocalDateTime.now();
        dispute.startReview(admin, now);
        Dispute saved = disputeRepository.save(dispute);
        recordEvent(saved, admin, DisputeEventType.CLAIMED, "Spór podjęty przez administratora", now);
        notifyParticipants(
                saved,
                NotificationType.DISPUTE_CLAIMED,
                "Spór jest analizowany",
                "Administrator rozpoczął analizę sporu dotyczącego zlecenia „" + saved.getJob().getTitle() + "”."
        );
        return toDetail(saved);
    }

    @Transactional
    public DisputeDetailResponse resolveDispute(Long disputeId, ResolveDisputeRequest request, User admin) {
        assertAdmin(admin);
        Dispute dispute = getForUpdate(disputeId);

        if (dispute.getStatus() == DisputeStatus.RESOLVED || dispute.getStatus() == DisputeStatus.CANCELLED) {
            throw new ConflictException("Ten spór jest już zamknięty");
        }

        if (dispute.getAssignedAdmin() != null && !sameUser(dispute.getAssignedAdmin(), admin)) {
            throw new ConflictException("Spór jest przypisany do innego administratora");
        }

        LocalDateTime now = LocalDateTime.now();
        if (dispute.getStatus() == DisputeStatus.OPEN) {
            dispute.startReview(admin, now);
            recordEvent(dispute, admin, DisputeEventType.CLAIMED, "Spór automatycznie podjęty przy rozstrzygnięciu", now);
        }

        Job job = jobRepository.findByIdForUpdate(dispute.getJob().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Zlecenie nie istnieje"));
        assertJobIsDisputed(job);

        DisputeResolution resolution = request.resolution();
        switch (resolution) {
            case RELEASE_TO_WORKER -> {
                if (job.getTakenBy() == null) {
                    throw new ConflictException("Zlecenie nie ma wykonawcy, któremu można wypłacić środki");
                }
                transactionService.releaseMoney(job, job.getTakenBy());
                job.complete(now);
            }
            case REFUND_TO_REQUESTER -> {
                transactionService.refundMoney(job);
                job.cancel(now);
            }
            case RESUME_JOB -> {
                transactionService.assertHeld(job);
                job.restoreAfterDispute(dispute.getPreviousJobStatus());
            }
        }

        jobRepository.save(job);
        String note = request.note().trim();
        dispute.resolve(admin, resolution, note, now);
        Dispute saved = disputeRepository.save(dispute);
        recordEvent(saved, admin, DisputeEventType.RESOLVED, resolution.name() + ": " + note, now);
        notifyParticipants(
                saved,
                NotificationType.DISPUTE_RESOLVED,
                "Spór rozstrzygnięty",
                resolutionMessage(saved, resolution)
        );

        return toDetail(saved);
    }

    private Dispute getForRead(Long disputeId) {
        return disputeRepository.findById(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException("Spór nie istnieje"));
    }

    private Dispute getForUpdate(Long disputeId) {
        return disputeRepository.findByIdForUpdate(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException("Spór nie istnieje"));
    }

    private void assertParticipant(Job job, User user) {
        if (!sameUser(job.getCreatedBy(), user) && !sameUser(job.getTakenBy(), user)) {
            throw new ForbiddenOperationException("Tylko strony zlecenia mogą otworzyć spór");
        }
    }

    private void assertParticipantOrAdmin(Dispute dispute, User user) {
        if (user != null && user.getRole() == UserRole.ADMIN) {
            return;
        }
        assertParticipant(dispute.getJob(), user);
    }

    private void assertAdmin(User user) {
        if (user == null || user.getRole() != UserRole.ADMIN) {
            throw new ForbiddenOperationException("Ta operacja wymaga uprawnień administratora");
        }
    }

    private void assertJobIsDisputed(Job job) {
        if (job.getStatus() != JobStatus.DISPUTED) {
            throw new ConflictException("Zlecenie nie jest aktualnie objęte sporem");
        }
    }

    private User otherParticipant(Job job, User currentUser) {
        if (sameUser(job.getCreatedBy(), currentUser)) {
            if (job.getTakenBy() == null) {
                throw new ConflictException("Zlecenie nie ma wykonawcy");
            }
            return job.getTakenBy();
        }
        if (sameUser(job.getTakenBy(), currentUser)) {
            return job.getCreatedBy();
        }
        throw new ForbiddenOperationException("Nie jesteś stroną tego zlecenia");
    }

    private boolean sameUser(User first, User second) {
        return first != null
                && second != null
                && first.getId() != null
                && first.getId().equals(second.getId());
    }

    private void notifyParticipants(
            Dispute dispute,
            NotificationType type,
            String title,
            String body
    ) {
        Job job = dispute.getJob();
        notificationService.notify(job.getCreatedBy(), type, title, body, job, dispute);
        if (job.getTakenBy() != null && !sameUser(job.getTakenBy(), job.getCreatedBy())) {
            notificationService.notify(job.getTakenBy(), type, title, body, job, dispute);
        }
    }

    private String resolutionMessage(Dispute dispute, DisputeResolution resolution) {
        String action = switch (resolution) {
            case RELEASE_TO_WORKER -> "środki zostały wypłacone wykonawcy";
            case REFUND_TO_REQUESTER -> "środki zostały zwrócone zlecającemu";
            case RESUME_JOB -> "zlecenie zostało wznowione";
        };
        return "Spór dotyczący zlecenia „" + dispute.getJob().getTitle() + "” został rozstrzygnięty: " + action + ".";
    }

    private void recordEvent(
            Dispute dispute,
            User actor,
            DisputeEventType type,
            String note,
            LocalDateTime at
    ) {
        DisputeEvent event = new DisputeEvent();
        event.setDispute(dispute);
        event.setActor(actor);
        event.setEventType(type);
        event.setNote(note);
        event.setCreatedAt(at);
        eventRepository.save(event);
    }

    private DisputeDetailResponse toDetail(Dispute dispute) {
        List<DisputeEventResponse> events = eventRepository.findByDispute_IdOrderByCreatedAtAsc(dispute.getId())
                .stream()
                .map(event -> new DisputeEventResponse(
                        event.getId(),
                        event.getActor().getId(),
                        event.getActor().getNickname(),
                        event.getEventType(),
                        event.getNote(),
                        event.getCreatedAt()
                ))
                .toList();
        return new DisputeDetailResponse(toResponse(dispute), events);
    }

    private DisputeResponse toResponse(Dispute dispute) {
        Job job = dispute.getJob();
        return new DisputeResponse(
                dispute.getId(),
                job.getId(),
                job.getTitle(),
                job.getCreatedBy().getId(),
                job.getTakenBy() != null ? job.getTakenBy().getId() : null,
                dispute.getOpenedBy().getId(),
                dispute.getAssignedAdmin() != null ? dispute.getAssignedAdmin().getId() : null,
                dispute.getReason(),
                dispute.getDescription(),
                dispute.getStatus(),
                dispute.getPreviousJobStatus(),
                dispute.getResolution(),
                dispute.getAdminNote(),
                dispute.getOpenedAt(),
                dispute.getReviewStartedAt(),
                dispute.getResolvedAt(),
                dispute.getCancelledAt()
        );
    }
}
