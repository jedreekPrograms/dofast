package com.doFast.dofastapp.job.service;

import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.job.dto.JobRequest;
import com.doFast.dofastapp.job.dto.JobResponse;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.repository.JobRepository;
import com.doFast.dofastapp.location.dto.LocationRequest;
import com.doFast.dofastapp.location.dto.LocationResponse;
import com.doFast.dofastapp.location.service.GeoPointFactory;
import com.doFast.dofastapp.payment.service.TransactionService;
import com.doFast.dofastapp.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private TransactionService transactionService;

    private JobService jobService;
    private User owner;
    private User worker;

    @BeforeEach
    void setUp() {
        jobService = new JobService(jobRepository, transactionService);
        owner = user(1L, "owner@example.com");
        worker = user(2L, "worker@example.com");
    }

    @Test
    void createJobStartsOpenStoresOnlyPublicLabelInPublicResponseAndLocksFunds() {
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JobRequest request = new JobRequest();
        request.setTitle("Zakupy z Biedronki");
        request.setDescription("Kup podstawowe zakupy i dostarcz pod wskazany adres.");
        request.setPrice(new BigDecimal("25.00"));
        request.setLocation(location(
                "51.1128",
                "17.0601",
                "Wrocław, Plac Grunwaldzki",
                "ul. Grunwaldzka 10, mieszkanie 5"
        ));

        JobResponse response = jobService.createJob(request, owner);

        assertEquals(JobStatus.OPEN, response.status());
        assertEquals(owner.getId(), response.createdById());
        assertEquals("Wrocław, Plac Grunwaldzki", response.locationLabel());
        verify(transactionService).holdMoney(any(Job.class));
    }

    @Test
    void ownerCannotAcceptOwnJob() {
        Job job = job(JobStatus.OPEN, owner, null);
        when(jobRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(job));

        assertThrows(ForbiddenOperationException.class, () -> jobService.acceptJob(10L, owner));
    }

    @Test
    void unavailableJobCannotBeAcceptedAgain() {
        Job job = job(JobStatus.IN_PROGRESS, owner, worker);
        when(jobRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(job));

        assertThrows(ConflictException.class, () -> jobService.acceptJob(10L, user(3L, "other@example.com")));
    }

    @Test
    void workerRequestsCompletionBeforeOwnerCanConfirm() {
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Job job = job(JobStatus.IN_PROGRESS, owner, worker);
        when(jobRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(job));

        JobResponse response = jobService.requestCompletion(10L, worker);

        assertEquals(JobStatus.COMPLETION_REQUESTED, response.status());
    }

    @Test
    void ownerConfirmingCompletionReleasesEscrowToWorker() {
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Job job = job(JobStatus.COMPLETION_REQUESTED, owner, worker);
        when(jobRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(job));

        JobResponse response = jobService.confirmCompletion(10L, owner);

        assertEquals(JobStatus.DONE, response.status());
        verify(transactionService).releaseMoney(job, worker);
    }

    @Test
    void acceptedJobCannotBeCancelledDirectly() {
        Job job = job(JobStatus.IN_PROGRESS, owner, worker);
        when(jobRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(job));

        assertThrows(ConflictException.class, () -> jobService.cancelJob(10L, owner));
    }

    @Test
    void assignedWorkerCanReadExactLocationAndPrivateLabelWhileJobIsActive() {
        Job job = job(JobStatus.IN_PROGRESS, owner, worker);
        when(jobRepository.findById(10L)).thenReturn(Optional.of(job));

        LocationResponse response = jobService.getExactLocation(10L, worker);

        assertEquals(51.1128, response.latitude(), 0.000001);
        assertEquals(17.0601, response.longitude(), 0.000001);
        assertEquals("ul. Grunwaldzka 10, mieszkanie 5", response.label());
    }

    @Test
    void unrelatedUserCannotReadExactLocation() {
        Job job = job(JobStatus.IN_PROGRESS, owner, worker);
        when(jobRepository.findById(10L)).thenReturn(Optional.of(job));

        User stranger = user(3L, "stranger@example.com");

        assertThrows(ForbiddenOperationException.class, () -> jobService.getExactLocation(10L, stranger));
    }

    @Test
    void workerCannotReadExactLocationAfterCompletion() {
        Job job = job(JobStatus.DONE, owner, worker);
        when(jobRepository.findById(10L)).thenReturn(Optional.of(job));

        assertThrows(ForbiddenOperationException.class, () -> jobService.getExactLocation(10L, worker));
    }

    private Job job(JobStatus status, User createdBy, User takenBy) {
        Job job = new Job();
        ReflectionTestUtils.setField(job, "id", 10L);
        job.setTitle("Test job");
        job.setDescription("Test job description");
        job.setPrice(new BigDecimal("20.00"));
        job.setStatus(status);
        job.setLocation(GeoPointFactory.from(location(
                "51.1128",
                "17.0601",
                "Wrocław, Plac Grunwaldzki",
                "ul. Grunwaldzka 10, mieszkanie 5"
        )));
        job.setLocationLabel("Wrocław, Plac Grunwaldzki");
        job.setLocationPrivateLabel("ul. Grunwaldzka 10, mieszkanie 5");
        job.setCreatedBy(createdBy);
        job.setTakenBy(takenBy);
        return job;
    }

    private LocationRequest location(String latitude, String longitude, String publicLabel, String privateLabel) {
        LocationRequest request = new LocationRequest();
        request.setLatitude(new BigDecimal(latitude));
        request.setLongitude(new BigDecimal(longitude));
        request.setPublicLabel(publicLabel);
        request.setPrivateLabel(privateLabel);
        return request;
    }

    private User user(Long id, String email) {
        User user = new User(email, email);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
