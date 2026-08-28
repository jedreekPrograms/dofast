package com.doFast.dofastapp.job.proposal;

import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.common.exception.BusinessException;
import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
import com.doFast.dofastapp.job.assignment.JobAssignmentMode;
import com.doFast.dofastapp.job.category.FulfillmentMode;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.repository.JobRepository;
import com.doFast.dofastapp.job.service.JobResponseMapper;
import com.doFast.dofastapp.location.tracking.service.LiveTrackingService;
import com.doFast.dofastapp.notification.enums.NotificationType;
import com.doFast.dofastapp.notification.service.NotificationService;
import com.doFast.dofastapp.payment.service.TransactionService;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.service.UserBlockService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class JobProposalService {

    private final JobRepository jobRepository;
    private final JobProposalRepository jobProposalRepository;
    private final TransactionService transactionService;
    private final NotificationService notificationService;
    private final LiveTrackingService liveTrackingService;
    private final UserBlockService userBlockService;

    public JobProposalService(
            JobRepository jobRepository,
            JobProposalRepository jobProposalRepository,
            TransactionService transactionService,
            NotificationService notificationService,
            LiveTrackingService liveTrackingService,
            UserBlockService userBlockService
    ) {
        this.jobRepository = jobRepository;
        this.jobProposalRepository = jobProposalRepository;
        this.transactionService = transactionService;
        this.notificationService = notificationService;
        this.liveTrackingService = liveTrackingService;
        this.userBlockService = userBlockService;
    }

    @Transactional
    public JobProposalResponse submit(Long jobId, CreateJobProposalRequest request, User proposer) {
        Job job = getJobForUpdate(jobId);
        assertProposalJob(job);
        if (job.getStatus() != JobStatus.OPEN) {
            throw new ConflictException("Zlecenie nie przyjmuje już propozycji");
        }
        if (sameUser(job.getCreatedBy(), proposer)) {
            throw new ForbiddenOperationException("Nie możesz złożyć propozycji do własnego zlecenia");
        }
        if (userBlockService.isInteractionBlocked(job.getCreatedBy(), proposer)) {
            throw new ForbiddenOperationException("Nie możesz złożyć propozycji do tego zlecenia");
        }
        if (jobProposalRepository.findByJob_IdAndProposer_Id(jobId, proposer.getId()).isPresent()) {
            throw new ConflictException("Masz już propozycję dla tego zlecenia");
        }

        BigDecimal amount = resolveAmount(job, request.amount());
        JobProposal proposal = jobProposalRepository.save(
                new JobProposal(job, proposer, amount, normalizeMessage(request.message()))
        );

        notificationService.notify(
                job.getCreatedBy(),
                NotificationType.JOB_PROPOSAL_RECEIVED,
                "Nowa propozycja do zlecenia",
                proposer.getNickname() + " wysłał propozycję do „" + job.getTitle() + "” za " + amount.toPlainString() + " zł.",
                job,
                null
        );
        return toResponse(proposal);
    }

    public List<JobProposalResponse> listVisible(Long jobId, User viewer) {
        Job job = getJob(jobId);
        assertProposalJob(job);

        if (sameUser(job.getCreatedBy(), viewer)) {
            return jobProposalRepository.findAllByJob_IdOrderByCreatedAtAscIdAsc(jobId)
                    .stream()
                    .map(this::toResponse)
                    .toList();
        }

        return jobProposalRepository.findByJob_IdAndProposer_Id(jobId, viewer.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void withdraw(Long jobId, Long proposalId, User proposer) {
        Job job = getJobForUpdate(jobId);
        assertProposalJob(job);
        JobProposal proposal = getProposal(jobId, proposalId);
        if (!sameUser(proposal.getProposer(), proposer)) {
            throw new ForbiddenOperationException("Możesz wycofać tylko własną propozycję");
        }
        if (proposal.getStatus() != JobProposalStatus.SUBMITTED) {
            throw new ConflictException("Tej propozycji nie można już wycofać");
        }
        if (job.getStatus() != JobStatus.OPEN) {
            throw new ConflictException("Zlecenie nie przyjmuje już zmian propozycji");
        }

        proposal.withdraw(LocalDateTime.now());
        jobProposalRepository.save(proposal);
    }

    @Transactional
    public AcceptedJobProposalResponse accept(Long jobId, Long proposalId, User requester) {
        Job job = getJobForUpdate(jobId);
        assertProposalJob(job);
        if (!sameUser(job.getCreatedBy(), requester)) {
            throw new ForbiddenOperationException("Tylko zleceniodawca może wybrać propozycję");
        }
        if (job.getStatus() != JobStatus.OPEN) {
            throw new ConflictException("Zlecenie nie jest już otwarte");
        }

        JobProposal proposal = getProposal(jobId, proposalId);
        if (proposal.getStatus() != JobProposalStatus.SUBMITTED) {
            throw new ConflictException("Propozycja nie jest już aktywna");
        }
        User worker = proposal.getProposer();
        if (userBlockService.isInteractionBlocked(job.getCreatedBy(), worker)) {
            throw new ForbiddenOperationException("Nie możesz zaakceptować tej propozycji");
        }

        // The original published budget is already held. Adjust the held escrow first;
        // if the requester lacks the delta, the whole transaction rolls back and the job stays OPEN.
        transactionService.adjustHeldAmount(job, proposal.getAmount(), proposal.getId());
        job.setPrice(proposal.getAmount());
        job.assignTo(worker, LocalDateTime.now());
        Job savedJob = jobRepository.save(job);

        LocalDateTime acceptedAt = LocalDateTime.now();
        proposal.accept(acceptedAt);
        List<JobProposal> submitted = jobProposalRepository.findAllByJob_IdAndStatusOrderByCreatedAtAscIdAsc(
                jobId,
                JobProposalStatus.SUBMITTED
        );
        submitted.stream()
                .filter(other -> !other.getId().equals(proposal.getId()))
                .forEach(JobProposal::reject);
        jobProposalRepository.saveAll(submitted);
        jobProposalRepository.save(proposal);

        if (usesLiveTracking(savedJob)) {
            liveTrackingService.initializeForAcceptedJob(savedJob);
        }
        notificationService.notify(
                worker,
                NotificationType.JOB_PROPOSAL_ACCEPTED,
                "Twoja propozycja została zaakceptowana",
                "Zleceniodawca wybrał Twoją propozycję do „" + savedJob.getTitle() + "”.",
                savedJob,
                null
        );

        return new AcceptedJobProposalResponse(
                JobResponseMapper.toResponse(savedJob),
                toResponse(proposal)
        );
    }

    private BigDecimal resolveAmount(Job job, BigDecimal requestedAmount) {
        BigDecimal amount = requestedAmount == null ? job.getPrice() : requestedAmount;
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException("Kwota propozycji musi być dodatnia");
        }
        if (!job.isPriceNegotiationEnabled() && amount.compareTo(job.getPrice()) != 0) {
            throw new BusinessException("Zleceniodawca nie włączył negocjacji ceny");
        }
        return amount;
    }

    private String normalizeMessage(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private Job getJob(Long jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Zlecenie nie istnieje"));
    }

    private Job getJobForUpdate(Long jobId) {
        return jobRepository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Zlecenie nie istnieje"));
    }

    private JobProposal getProposal(Long jobId, Long proposalId) {
        return jobProposalRepository.findByIdAndJob_Id(proposalId, jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Propozycja nie istnieje"));
    }

    private void assertProposalJob(Job job) {
        if (job.getAssignmentMode() != JobAssignmentMode.PROPOSALS) {
            throw new ConflictException("To zlecenie korzysta z natychmiastowego przyjęcia");
        }
    }

    private boolean usesLiveTracking(Job job) {
        return job.getCategory() != null && job.getCategory().getFulfillmentMode() == FulfillmentMode.POINT_TO_POINT;
    }

    private boolean sameUser(User first, User second) {
        return first != null
                && second != null
                && first.getId() != null
                && first.getId().equals(second.getId());
    }

    private JobProposalResponse toResponse(JobProposal proposal) {
        return new JobProposalResponse(
                proposal.getId(),
                proposal.getJob().getId(),
                proposal.getProposer().getId(),
                proposal.getAmount(),
                proposal.getMessage(),
                proposal.getStatus(),
                proposal.getCreatedAt(),
                proposal.getUpdatedAt(),
                proposal.getAcceptedAt(),
                proposal.getWithdrawnAt()
        );
    }
}
