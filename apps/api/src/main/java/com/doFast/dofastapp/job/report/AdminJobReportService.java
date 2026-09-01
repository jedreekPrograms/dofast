package com.doFast.dofastapp.job.report;

import com.doFast.dofastapp.common.dto.PageResponse;
import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.expense.JobExpenseService;
import com.doFast.dofastapp.job.repository.JobRepository;
import com.doFast.dofastapp.notification.enums.NotificationType;
import com.doFast.dofastapp.notification.service.NotificationService;
import com.doFast.dofastapp.payment.service.TransactionService;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.enums.UserRole;
import com.doFast.dofastapp.user.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

@Service
public class AdminJobReportService {

    private static final Set<JobStatus> ACCOUNT_SUSPENSION_BLOCKING_STATUSES = Set.of(
            JobStatus.IN_PROGRESS,
            JobStatus.COMPLETION_REQUESTED,
            JobStatus.DISPUTED
    );

    private final JobReportRepository repository;
    private final JobReportEnforcementRepository enforcementRepository;
    private final JobReportAccountEnforcementRepository accountEnforcementRepository;
    private final JobRepository jobRepository;
    private final NotificationService notificationService;
    private final TransactionService transactionService;
    private final JobExpenseService expenseService;

    public AdminJobReportService(
            JobReportRepository repository,
            JobReportEnforcementRepository enforcementRepository,
            JobReportAccountEnforcementRepository accountEnforcementRepository,
            JobRepository jobRepository,
            NotificationService notificationService,
            TransactionService transactionService,
            JobExpenseService expenseService
    ) {
        this.repository = repository;
        this.enforcementRepository = enforcementRepository;
        this.accountEnforcementRepository = accountEnforcementRepository;
        this.jobRepository = jobRepository;
        this.notificationService = notificationService;
        this.transactionService = transactionService;
        this.expenseService = expenseService;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminJobReportResponse> list(JobReportStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        Page<JobReport> reports = status == null
                ? repository.findAllByOrderByCreatedAtAsc(pageable)
                : repository.findAllByStatusOrderByCreatedAtAsc(status, pageable);
        return PageResponse.from(reports, reports.stream().map(AdminJobReportResponse::from).toList());
    }

    @Transactional(readOnly = true)
    public Optional<JobReportEnforcementResponse> enforcement(Long reportId) {
        return enforcementRepository.findByReport_Id(reportId)
                .map(JobReportEnforcementResponse::from);
    }

    @Transactional(readOnly = true)
    public Optional<JobReportAccountEnforcementResponse> accountEnforcement(Long reportId) {
        return accountEnforcementRepository.findByReport_Id(reportId)
                .map(JobReportAccountEnforcementResponse::from);
    }

    @Transactional
    public AdminJobReportResponse moderate(Long id, ModerateJobReportRequest request, User moderator) {
        if (request.status() != JobReportStatus.REVIEWED && request.status() != JobReportStatus.DISMISSED) {
            throw new ConflictException("Moderation decision must be REVIEWED or DISMISSED");
        }

        JobReport report = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Zgłoszenie nie istnieje"));
        if (report.getStatus() != JobReportStatus.SUBMITTED) {
            throw new ConflictException("Zgłoszenie zostało już rozpatrzone lub wycofane");
        }

        String note = normalize(request.note());
        report.moderate(request.status(), moderator, note);
        notifyReporter(report);
        return AdminJobReportResponse.from(report);
    }

    @Transactional
    public JobReportEnforcementResponse enforce(Long id, EnforceJobReportRequest request, User moderator) {
        JobReport report = reviewedReport(id);

        if (enforcementRepository.existsByReport_Id(id)) {
            throw new ConflictException("Dla tego zgłoszenia wykonano już akcję egzekucyjną");
        }
        if (request.action() != JobReportEnforcementAction.CANCEL_OPEN_JOB) {
            throw new ConflictException("Nieobsługiwana akcja egzekucyjna");
        }

        Job job = report.getJob();
        if (job.getStatus() != JobStatus.OPEN) {
            throw new ConflictException("Moderacyjne anulowanie jest dozwolone wyłącznie dla otwartego zlecenia");
        }

        cancelOpenJobWithRefund(job, LocalDateTime.now());
        JobReportEnforcement enforcement = new JobReportEnforcement(
                report,
                job,
                moderator,
                request.action(),
                normalize(request.reason())
        );
        enforcementRepository.save(enforcement);
        return JobReportEnforcementResponse.from(enforcement);
    }

    @Transactional
    public JobReportAccountEnforcementResponse enforceAccount(
            Long id,
            EnforceJobReportAccountRequest request,
            User moderator
    ) {
        JobReport report = reviewedReport(id);
        if (accountEnforcementRepository.existsByReport_Id(id)) {
            throw new ConflictException("Dla tego zgłoszenia wykonano już sankcję na koncie");
        }
        if (request.action() != JobReportAccountEnforcementAction.SUSPEND_JOB_OWNER) {
            throw new ConflictException("Nieobsługiwana sankcja na koncie");
        }

        User target = report.getJob().getCreatedBy();
        if (target.getId().equals(moderator.getId())) {
            throw new ConflictException("Moderator nie może zawiesić własnego konta");
        }
        if (target.getRole() == UserRole.ADMIN) {
            throw new ConflictException("Ta ścieżka moderacyjna nie może zawieszać kont administratorów");
        }
        if (target.getStatus() != UserStatus.ACTIVE) {
            throw new ConflictException("Konto użytkownika jest już zawieszone");
        }
        if (jobRepository.existsParticipantJobWithStatusIn(target, ACCOUNT_SUSPENSION_BLOCKING_STATUSES)) {
            throw new ConflictException(
                    "Nie można zawiesić konta uczestniczącego w aktywnym zleceniu, sporze lub rozliczeniu"
            );
        }

        LocalDateTime now = LocalDateTime.now();
        jobRepository.findAllByStatusAndCreatedBy(JobStatus.OPEN, target)
                .forEach(job -> cancelOpenJobWithRefund(job, now));
        target.setStatus(UserStatus.SUSPENDED);

        JobReportAccountEnforcement enforcement = new JobReportAccountEnforcement(
                report,
                target,
                moderator,
                request.action(),
                normalize(request.reason())
        );
        accountEnforcementRepository.save(enforcement);
        return JobReportAccountEnforcementResponse.from(enforcement);
    }

    private void cancelOpenJobWithRefund(Job job, LocalDateTime now) {
        job.cancel(now);
        transactionService.refundMoney(job);
        expenseService.refundAll(job);
    }

    private void notifyReporter(JobReport report) {
        boolean reviewed = report.getStatus() == JobReportStatus.REVIEWED;
        notificationService.notify(
                report.getReporter(),
                reviewed ? NotificationType.JOB_REPORT_REVIEWED : NotificationType.JOB_REPORT_DISMISSED,
                reviewed ? "Zgłoszenie zostało potwierdzone" : "Zgłoszenie zostało rozpatrzone",
                reviewed
                        ? "Moderacja potwierdziła Twoje zgłoszenie. Ewentualne dalsze działania są obsługiwane oddzielnie."
                        : "Moderacja przeanalizowała zgłoszenie i nie potwierdziła naruszenia na podstawie dostępnych informacji.",
                report.getJob(),
                null
        );
    }

    private JobReport reviewedReport(Long id) {
        JobReport report = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Zgłoszenie nie istnieje"));
        if (report.getStatus() != JobReportStatus.REVIEWED) {
            throw new ConflictException("Akcję egzekucyjną można wykonać tylko dla potwierdzonego zgłoszenia");
        }
        return report;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
