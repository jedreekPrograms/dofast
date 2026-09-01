package com.doFast.dofastapp.job.report;

import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.expense.JobExpenseService;
import com.doFast.dofastapp.job.repository.JobRepository;
import com.doFast.dofastapp.notification.enums.NotificationType;
import com.doFast.dofastapp.notification.service.NotificationService;
import com.doFast.dofastapp.payment.service.TransactionService;
import com.doFast.dofastapp.user.auth.session.AuthRefreshSessionRepository;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.enums.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminJobReportServiceTest {

    @Mock private JobReportRepository repository;
    @Mock private JobReportEnforcementRepository enforcementRepository;
    @Mock private JobReportAccountEnforcementRepository accountEnforcementRepository;
    @Mock private JobRepository jobRepository;
    @Mock private NotificationService notificationService;
    @Mock private TransactionService transactionService;
    @Mock private JobExpenseService expenseService;
    @Mock private AuthRefreshSessionRepository refreshSessionRepository;

    private AdminJobReportService service;
    private JobReport report;
    private User reporter;
    private User admin;
    private User owner;
    private Job job;

    @BeforeEach
    void setUp() {
        service = new AdminJobReportService(
                repository,
                enforcementRepository,
                accountEnforcementRepository,
                jobRepository,
                notificationService,
                transactionService,
                expenseService,
                refreshSessionRepository
        );
        reporter = new User("reporter@example.com", "Reporter");
        owner = new User("owner@example.com", "Owner");
        admin = new User("admin@example.com", "Admin");
        ReflectionTestUtils.setField(reporter, "id", 7L);
        ReflectionTestUtils.setField(owner, "id", 8L);
        ReflectionTestUtils.setField(admin, "id", 9L);
        job = new Job();
        ReflectionTestUtils.setField(job, "id", 11L);
        job.setCreatedBy(owner);
        job.setStatus(JobStatus.OPEN);
        report = new JobReport(job, reporter, JobReportReason.FRAUD, "suspicious");
        ReflectionTestUtils.setField(report, "id", 15L);
    }

    @Test
    void recordsAuditedModerationDecisionAndNotifiesReporter() {
        when(repository.findById(15L)).thenReturn(Optional.of(report));

        AdminJobReportResponse response = service.moderate(
                15L,
                new ModerateJobReportRequest(JobReportStatus.REVIEWED, "  confirmed by evidence  "),
                admin
        );

        assertEquals(JobReportStatus.REVIEWED, response.status());
        assertEquals(9L, response.reviewedById());
        assertEquals("confirmed by evidence", response.moderationNote());
        assertNotNull(response.reviewedAt());
        verify(notificationService).notify(
                eq(reporter),
                eq(NotificationType.JOB_REPORT_REVIEWED),
                eq("Zgłoszenie zostało potwierdzone"),
                any(String.class),
                eq(job),
                eq(null)
        );
    }

    @Test
    void notifiesReporterWhenReportIsDismissed() {
        when(repository.findById(15L)).thenReturn(Optional.of(report));

        AdminJobReportResponse response = service.moderate(
                15L,
                new ModerateJobReportRequest(JobReportStatus.DISMISSED, "not enough evidence"),
                admin
        );

        assertEquals(JobReportStatus.DISMISSED, response.status());
        verify(notificationService).notify(
                eq(reporter),
                eq(NotificationType.JOB_REPORT_DISMISSED),
                eq("Zgłoszenie zostało rozpatrzone"),
                any(String.class),
                eq(job),
                eq(null)
        );
    }

    @Test
    void returnsPersistedEnforcementAudit() {
        report.moderate(JobReportStatus.REVIEWED, admin, "confirmed");
        JobReportEnforcement enforcement = new JobReportEnforcement(
                report,
                job,
                admin,
                JobReportEnforcementAction.CANCEL_OPEN_JOB,
                "policy violation"
        );
        ReflectionTestUtils.setField(enforcement, "id", 21L);
        ReflectionTestUtils.setField(enforcement, "createdAt", java.time.LocalDateTime.now());
        when(enforcementRepository.findByReport_Id(15L)).thenReturn(Optional.of(enforcement));

        Optional<JobReportEnforcementResponse> response = service.enforcement(15L);

        assertTrue(response.isPresent());
        assertEquals(21L, response.orElseThrow().id());
        assertEquals(JobReportEnforcementAction.CANCEL_OPEN_JOB, response.orElseThrow().action());
        assertEquals("policy violation", response.orElseThrow().reason());
    }

    @Test
    void cancelsReviewedOpenJobRefundsFundingAndPersistsAuditRecord() {
        report.moderate(JobReportStatus.REVIEWED, admin, "confirmed");
        when(repository.findById(15L)).thenReturn(Optional.of(report));
        when(enforcementRepository.existsByReport_Id(15L)).thenReturn(false);

        JobReportEnforcementResponse response = service.enforce(
                15L,
                new EnforceJobReportRequest(JobReportEnforcementAction.CANCEL_OPEN_JOB, "  prohibited listing  "),
                admin
        );

        assertEquals(JobStatus.CANCELLED, job.getStatus());
        assertEquals(JobReportEnforcementAction.CANCEL_OPEN_JOB, response.action());
        assertEquals("prohibited listing", response.reason());
        verify(transactionService).refundMoney(job);
        verify(expenseService).refundAll(job);
        verify(enforcementRepository).save(any(JobReportEnforcement.class));
    }

    @Test
    void suspendsReviewedJobOwnerAndRefundsAllCancelledOpenListings() {
        report.moderate(JobReportStatus.REVIEWED, admin, "confirmed");
        Job secondOpenJob = new Job();
        ReflectionTestUtils.setField(secondOpenJob, "id", 12L);
        secondOpenJob.setCreatedBy(owner);
        secondOpenJob.setStatus(JobStatus.OPEN);
        when(repository.findById(15L)).thenReturn(Optional.of(report));
        when(accountEnforcementRepository.existsByReport_Id(15L)).thenReturn(false);
        when(jobRepository.existsParticipantJobWithStatusIn(any(), any())).thenReturn(false);
        when(jobRepository.findAllByStatusAndCreatedBy(JobStatus.OPEN, owner))
                .thenReturn(List.of(job, secondOpenJob));

        JobReportAccountEnforcementResponse response = service.enforceAccount(
                15L,
                new EnforceJobReportAccountRequest(
                        JobReportAccountEnforcementAction.SUSPEND_JOB_OWNER,
                        "  repeated fraud  "
                ),
                admin
        );

        assertEquals(UserStatus.SUSPENDED, owner.getStatus());
        assertEquals(1L, owner.getAuthVersion());
        assertEquals(JobStatus.CANCELLED, job.getStatus());
        assertEquals(JobStatus.CANCELLED, secondOpenJob.getStatus());
        assertEquals(8L, response.targetUserId());
        assertEquals("repeated fraud", response.reason());
        verify(transactionService).refundMoney(job);
        verify(transactionService).refundMoney(secondOpenJob);
        verify(expenseService).refundAll(job);
        verify(expenseService).refundAll(secondOpenJob);
        verify(refreshSessionRepository).revokeAllActiveForUser(
                eq(8L),
                eq("ACCOUNT_SUSPENDED"),
                any(LocalDateTime.class)
        );
        verify(accountEnforcementRepository).save(any(JobReportAccountEnforcement.class));
    }

    @Test
    void rejectsAccountSuspensionWhileTargetHasActiveLifecycleJobWithoutRefundingAnything() {
        report.moderate(JobReportStatus.REVIEWED, admin, "confirmed");
        when(repository.findById(15L)).thenReturn(Optional.of(report));
        when(accountEnforcementRepository.existsByReport_Id(15L)).thenReturn(false);
        when(jobRepository.existsParticipantJobWithStatusIn(any(), any())).thenReturn(true);

        assertThrows(
                ConflictException.class,
                () -> service.enforceAccount(
                        15L,
                        new EnforceJobReportAccountRequest(
                                JobReportAccountEnforcementAction.SUSPEND_JOB_OWNER,
                                null
                        ),
                        admin
                )
        );
        assertEquals(UserStatus.ACTIVE, owner.getStatus());
        assertEquals(0L, owner.getAuthVersion());
        verify(transactionService, never()).refundMoney(any());
        verify(expenseService, never()).refundAll(any());
        verify(refreshSessionRepository, never()).revokeAllActiveForUser(any(), any(), any());
    }

    @Test
    void rejectsEnforcementForUnreviewedReport() {
        when(repository.findById(15L)).thenReturn(Optional.of(report));

        assertThrows(
                ConflictException.class,
                () -> service.enforce(
                        15L,
                        new EnforceJobReportRequest(JobReportEnforcementAction.CANCEL_OPEN_JOB, null),
                        admin
                )
        );
    }

    @Test
    void rejectsEnforcementForActiveJob() {
        report.moderate(JobReportStatus.REVIEWED, admin, "confirmed");
        job.setStatus(JobStatus.IN_PROGRESS);
        when(repository.findById(15L)).thenReturn(Optional.of(report));
        when(enforcementRepository.existsByReport_Id(15L)).thenReturn(false);

        assertThrows(
                ConflictException.class,
                () -> service.enforce(
                        15L,
                        new EnforceJobReportRequest(JobReportEnforcementAction.CANCEL_OPEN_JOB, null),
                        admin
                )
        );
        verify(transactionService, never()).refundMoney(any());
        verify(expenseService, never()).refundAll(any());
    }

    @Test
    void rejectsReturningReportToSubmittedState() {
        assertThrows(
                ConflictException.class,
                () -> service.moderate(
                        15L,
                        new ModerateJobReportRequest(JobReportStatus.SUBMITTED, null),
                        admin
                )
        );
    }

    @Test
    void rejectsModeratorSettingWithdrawnState() {
        assertThrows(
                ConflictException.class,
                () -> service.moderate(
                        15L,
                        new ModerateJobReportRequest(JobReportStatus.WITHDRAWN, null),
                        admin
                )
        );
    }

    @Test
    void rejectsSecondModerationDecision() {
        report.moderate(JobReportStatus.DISMISSED, admin, null);
        when(repository.findById(15L)).thenReturn(Optional.of(report));

        assertThrows(
                ConflictException.class,
                () -> service.moderate(
                        15L,
                        new ModerateJobReportRequest(JobReportStatus.REVIEWED, null),
                        admin
                )
        );
    }
}
