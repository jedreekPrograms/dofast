package com.doFast.dofastapp.job.report;

import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminJobReportServiceTest {

    @Mock private JobReportRepository repository;
    @Mock private JobReportEnforcementRepository enforcementRepository;

    private AdminJobReportService service;
    private JobReport report;
    private User admin;
    private Job job;

    @BeforeEach
    void setUp() {
        service = new AdminJobReportService(repository, enforcementRepository);
        User reporter = new User("reporter@example.com", "Reporter");
        User owner = new User("owner@example.com", "Owner");
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
    void recordsAuditedModerationDecision() {
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
    }

    @Test
    void cancelsReviewedOpenJobAndPersistsAuditRecord() {
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
        verify(enforcementRepository).save(any(JobReportEnforcement.class));
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
