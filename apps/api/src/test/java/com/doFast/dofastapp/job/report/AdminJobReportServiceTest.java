package com.doFast.dofastapp.job.report;

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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminJobReportServiceTest {

    @Mock private JobReportRepository repository;

    private AdminJobReportService service;
    private JobReport report;
    private User admin;

    @BeforeEach
    void setUp() {
        service = new AdminJobReportService(repository);
        User reporter = new User("reporter@example.com", "Reporter");
        User owner = new User("owner@example.com", "Owner");
        admin = new User("admin@example.com", "Admin");
        ReflectionTestUtils.setField(reporter, "id", 7L);
        ReflectionTestUtils.setField(owner, "id", 8L);
        ReflectionTestUtils.setField(admin, "id", 9L);
        Job job = new Job();
        ReflectionTestUtils.setField(job, "id", 11L);
        job.setCreatedBy(owner);
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
