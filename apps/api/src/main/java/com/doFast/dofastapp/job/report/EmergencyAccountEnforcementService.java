package com.doFast.dofastapp.job.report;

import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
import com.doFast.dofastapp.dispute.entity.Dispute;
import com.doFast.dofastapp.dispute.entity.DisputeEvent;
import com.doFast.dofastapp.dispute.enums.DisputeEventType;
import com.doFast.dofastapp.dispute.enums.DisputeReason;
import com.doFast.dofastapp.dispute.enums.DisputeStatus;
import com.doFast.dofastapp.dispute.repository.DisputeEventRepository;
import com.doFast.dofastapp.dispute.repository.DisputeRepository;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.repository.JobRepository;
import com.doFast.dofastapp.location.tracking.service.LiveTrackingService;
import com.doFast.dofastapp.notification.enums.NotificationType;
import com.doFast.dofastapp.notification.service.NotificationService;
import com.doFast.dofastapp.payment.service.TransactionService;
import com.doFast.dofastapp.user.auth.session.AuthSessionService;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.enums.UserRole;
import com.doFast.dofastapp.user.enums.UserStatus;
import com.doFast.dofastapp.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
public class EmergencyAccountEnforcementService {

    private static final Set<JobStatus> PROTECTED_JOB_STATUSES = Set.of(
            JobStatus.IN_PROGRESS,
            JobStatus.COMPLETION_REQUESTED,
            JobStatus.DISPUTED
    );
    private static final EnumSet<DisputeStatus> ACTIVE_DISPUTE_STATUSES = EnumSet.of(
            DisputeStatus.OPEN,
            DisputeStatus.UNDER_REVIEW
    );
    private static final String SAFETY_DESCRIPTION =
            "Zlecenie zostało objęte kontrolą bezpieczeństwa przez moderację. "
                    + "Środki pozostają zabezpieczone do czasu rozstrzygnięcia sporu.";

    private final UserRepository userRepository;
    private final JobRepository jobRepository;
    private final DisputeRepository disputeRepository;
    private final DisputeEventRepository disputeEventRepository;
    private final TransactionService transactionService;
    private final LiveTrackingService liveTrackingService;
    private final AuthSessionService authSessionService;
    private final NotificationService notificationService;

    public EmergencyAccountEnforcementService(
            UserRepository userRepository,
            JobRepository jobRepository,
            DisputeRepository disputeRepository,
            DisputeEventRepository disputeEventRepository,
            TransactionService transactionService,
            LiveTrackingService liveTrackingService,
            AuthSessionService authSessionService,
            NotificationService notificationService
    ) {
        this.userRepository = userRepository;
        this.jobRepository = jobRepository;
        this.disputeRepository = disputeRepository;
        this.disputeEventRepository = disputeEventRepository;
        this.transactionService = transactionService;
        this.liveTrackingService = liveTrackingService;
        this.authSessionService = authSessionService;
        this.notificationService = notificationService;
    }

    @Transactional
    public User suspendJobOwner(Long targetUserId, User moderator) {
        User target = userRepository.findByIdForUpdate(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Użytkownik nie istnieje"));
        validateTarget(target, moderator);

        List<Job> protectedJobs = jobRepository.findAllParticipantJobsWithStatusInForUpdate(
                target,
                PROTECTED_JOB_STATUSES
        );
        for (Job job : protectedJobs) {
            containActiveJob(job, moderator);
            liveTrackingService.stopAndClear(job.getId());
        }

        LocalDateTime now = LocalDateTime.now();
        jobRepository.findAllByStatusAndCreatedBy(JobStatus.OPEN, target)
                .forEach(job -> job.cancel(now));

        target.incrementAuthVersion();
        target.setStatus(UserStatus.SUSPENDED);
        User suspended = userRepository.save(target);
        authSessionService.revokeAllForUser(suspended.getId(), "EMERGENCY_SUSPEND");
        return suspended;
    }

    private void containActiveJob(Job job, User moderator) {
        transactionService.assertHeld(job);
        if (job.getStatus() == JobStatus.DISPUTED) {
            disputeRepository.findFirstByJobAndStatusInOrderByOpenedAtDesc(job, ACTIVE_DISPUTE_STATUSES)
                    .orElseThrow(() -> new ConflictException(
                            "Zlecenie ma status sporny bez aktywnego rekordu sporu; awaryjna sankcja została zatrzymana"
                    ));
            return;
        }
        if (job.getStatus() != JobStatus.IN_PROGRESS && job.getStatus() != JobStatus.COMPLETION_REQUESTED) {
            throw new ConflictException("Nieobsługiwany stan aktywnego zlecenia podczas awaryjnej sankcji");
        }
        if (disputeRepository.findFirstByJobAndStatusInOrderByOpenedAtDesc(job, ACTIVE_DISPUTE_STATUSES).isPresent()) {
            throw new ConflictException(
                    "Aktywne zlecenie ma niespójny rekord sporu; awaryjna sankcja została zatrzymana"
            );
        }

        LocalDateTime now = LocalDateTime.now();
        JobStatus previousStatus = job.getStatus();
        job.markDisputed();
        jobRepository.save(job);

        Dispute dispute = new Dispute();
        dispute.setJob(job);
        dispute.setOpenedBy(moderator);
        dispute.setReason(DisputeReason.SAFETY_CONCERN);
        dispute.setDescription(SAFETY_DESCRIPTION);
        dispute.setStatus(DisputeStatus.OPEN);
        dispute.setPreviousJobStatus(previousStatus);
        dispute.setOpenedAt(now);
        dispute.startReview(moderator, now);
        Dispute saved = disputeRepository.save(dispute);

        recordEvent(saved, moderator, DisputeEventType.OPENED,
                "Spór bezpieczeństwa utworzony przez awaryjną akcję moderacyjną", now);
        recordEvent(saved, moderator, DisputeEventType.CLAIMED,
                "Spór automatycznie przypisany moderatorowi wykonującemu awaryjną sankcję", now);
        notifyParticipants(job, saved);
    }

    private void notifyParticipants(Job job, Dispute dispute) {
        String body = "Moderacja wstrzymała aktywne zlecenie „" + job.getTitle()
                + "”. Środki pozostają zabezpieczone do czasu rozstrzygnięcia sporu.";
        notificationService.notify(
                job.getCreatedBy(),
                NotificationType.DISPUTE_OPENED,
                "Zlecenie wstrzymane przez moderację",
                body,
                job,
                dispute
        );
        if (job.getTakenBy() != null && !sameUser(job.getTakenBy(), job.getCreatedBy())) {
            notificationService.notify(
                    job.getTakenBy(),
                    NotificationType.DISPUTE_OPENED,
                    "Zlecenie wstrzymane przez moderację",
                    body,
                    job,
                    dispute
            );
        }
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
        disputeEventRepository.save(event);
    }

    private void validateTarget(User target, User moderator) {
        if (target.getId().equals(moderator.getId())) {
            throw new ConflictException("Moderator nie może zawiesić własnego konta");
        }
        if (target.getRole() == UserRole.ADMIN) {
            throw new ConflictException("Awaryjna ścieżka moderacyjna nie może zawieszać kont administratorów");
        }
        if (target.getStatus() != UserStatus.ACTIVE) {
            throw new ConflictException("Konto użytkownika jest już zawieszone");
        }
    }

    private boolean sameUser(User first, User second) {
        return first != null
                && second != null
                && first.getId() != null
                && first.getId().equals(second.getId());
    }
}
