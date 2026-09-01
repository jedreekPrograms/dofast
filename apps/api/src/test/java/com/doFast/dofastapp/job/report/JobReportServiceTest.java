package com.doFast.dofastapp.job.report;

import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.repository.JobRepository;
import com.doFast.dofastapp.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobReportServiceTest {

    @Mock private JobReportRepository reportRepository;
    @Mock private JobRepository jobRepository;

    private JobReportService service;
    private User reporter;
    private User owner;
    private Job job;

    @BeforeEach
    void setUp() {
        service = new JobReportService(reportRepository, jobRepository);
        reporter = new User("reporter@example.com", "Reporter");
        owner = new User("owner@example.com", "Owner");
        ReflectionTestUtils.setField(reporter, "id", 7L);
        ReflectionTestUtils.setField(owner, "id", 8L);
        job = new Job();
        ReflectionTestUtils.setField(job, "id", 11L);
        job.setCreatedBy(owner);
        job.setStatus(JobStatus.OPEN);
    }

    @Test
    void createsReportAndNormalizesBlankDetails() {
        when(jobRepository.findByIdAndStatus(11L, JobStatus.OPEN)).thenReturn(Optional.of(job));
        when(reportRepository.existsByReporter_IdAndJob_Id(7L, 11L)).thenReturn(false);
        when(reportRepository.save(org.mockito.ArgumentMatchers.any(JobReport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.report(11L, new JobReportRequest(JobReportReason.FRAUD, "   "), reporter);

        ArgumentCaptor<JobReport> captor = ArgumentCaptor.forClass(JobReport.class);
        verify(reportRepository).save(captor.capture());
        assertEquals(JobReportReason.FRAUD, captor.getValue().getReason());
        assertEquals(null, captor.getValue().getDetails());
    }

    @Test
    void hidesUnavailableJobExistenceAndDoesNotCreateReport() {
        when(jobRepository.findByIdAndStatus(11L, JobStatus.OPEN)).thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> service.report(11L, new JobReportRequest(JobReportReason.FRAUD, "private"), reporter)
        );

        verify(jobRepository, never()).findById(11L);
        verify(reportRepository, never()).existsByReporter_IdAndJob_Id(7L, 11L);
        verify(reportRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void exposesResolutionTimestampInPrivateHistory() {
        JobReport report = new JobReport(job, reporter, JobReportReason.SPAM, null);
        report.moderate(JobReportStatus.REVIEWED, owner, "internal note");
        when(reportRepository.findAllByReporter_IdOrderByCreatedAtDesc(7L)).thenReturn(List.of(report));

        List<JobReportResponse> responses = service.mine(reporter);

        assertEquals(1, responses.size());
        assertEquals(JobReportStatus.REVIEWED, responses.getFirst().status());
        assertNotNull(responses.getFirst().reviewedAt());
    }

    @Test
    void withdrawsOwnPendingReportAndExposesTimestamp() {
        JobReport report = new JobReport(job, reporter, JobReportReason.OTHER, "mistake");
        ReflectionTestUtils.setField(report, "id", 31L);
        when(reportRepository.findByIdAndReporter_Id(31L, 7L)).thenReturn(Optional.of(report));
        when(reportRepository.save(report)).thenReturn(report);

        JobReportResponse response = service.withdraw(31L, reporter);

        assertEquals(JobReportStatus.WITHDRAWN, response.status());
        assertNotNull(response.withdrawnAt());
        verify(reportRepository).save(report);
    }

    @Test
    void rejectsWithdrawalAfterModerationStarted() {
        JobReport report = new JobReport(job, reporter, JobReportReason.SPAM, null);
        ReflectionTestUtils.setField(report, "id", 32L);
        report.moderate(JobReportStatus.DISMISSED, owner, "resolved");
        when(reportRepository.findByIdAndReporter_Id(32L, 7L)).thenReturn(Optional.of(report));

        assertThrows(ConflictException.class, () -> service.withdraw(32L, reporter));

        verify(reportRepository, never()).save(report);
    }

    @Test
    void rejectsOwnJob() {
        job.setCreatedBy(reporter);
        when(jobRepository.findByIdAndStatus(11L, JobStatus.OPEN)).thenReturn(Optional.of(job));

        assertThrows(
                ForbiddenOperationException.class,
                () -> service.report(11L, new JobReportRequest(JobReportReason.SPAM, null), reporter)
        );
        verify(reportRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsDuplicateReport() {
        when(jobRepository.findByIdAndStatus(11L, JobStatus.OPEN)).thenReturn(Optional.of(job));
        when(reportRepository.existsByReporter_IdAndJob_Id(7L, 11L)).thenReturn(true);

        assertThrows(
                ConflictException.class,
                () -> service.report(11L, new JobReportRequest(JobReportReason.OTHER, "duplicate"), reporter)
        );
        verify(reportRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
