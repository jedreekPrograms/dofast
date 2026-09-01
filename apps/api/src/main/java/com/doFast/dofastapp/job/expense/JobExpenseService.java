package com.doFast.dofastapp.job.expense;

import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.common.exception.BusinessException;
import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
import com.doFast.dofastapp.job.attachment.JobAttachment;
import com.doFast.dofastapp.job.attachment.JobAttachmentRepository;
import com.doFast.dofastapp.job.attachment.JobAttachmentVisibility;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.repository.JobRepository;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.enums.UserRole;
import com.doFast.dofastapp.wallet.enums.WalletTransactionType;
import com.doFast.dofastapp.wallet.service.WalletService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class JobExpenseService {

    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final BigDecimal MAX_BUDGET = new BigDecimal("10000.00");
    private static final Set<String> RECEIPT_MEDIA_TYPES = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/png",
            "image/webp"
    );
    private static final Set<WalletTransactionType> EXPENSE_SOURCE_DEBITS = Set.of(
            WalletTransactionType.EXPENSE_BUDGET_LOCK
    );

    private final JobRepository jobRepository;
    private final JobExpenseEscrowRepository escrowRepository;
    private final JobExpenseClaimRepository claimRepository;
    private final JobAttachmentRepository attachmentRepository;
    private final WalletService walletService;

    public JobExpenseService(
            JobRepository jobRepository,
            JobExpenseEscrowRepository escrowRepository,
            JobExpenseClaimRepository claimRepository,
            JobAttachmentRepository attachmentRepository,
            WalletService walletService
    ) {
        this.jobRepository = jobRepository;
        this.escrowRepository = escrowRepository;
        this.claimRepository = claimRepository;
        this.attachmentRepository = attachmentRepository;
        this.walletService = walletService;
    }

    @Transactional
    public void holdBudget(Job job) {
        BigDecimal budget = normalizeBudget(job.getExpenseBudget());
        if (budget.signum() == 0) return;
        if (job.getId() == null || job.getCreatedBy() == null || job.getCreatedBy().getId() == null) {
            throw new IllegalStateException("Expense budget requires a persisted job and payer");
        }
        if (escrowRepository.findByJob_Id(job.getId()).isPresent()) {
            throw new ConflictException("Budżet wydatków dla tego zlecenia został już zablokowany");
        }

        boolean debited = walletService.debit(
                job.getCreatedBy().getId(),
                budget,
                WalletTransactionType.EXPENSE_BUDGET_LOCK,
                job.getId(),
                lockOperationKey(job.getId())
        );
        if (!debited) {
            throw new ConflictException("Wykryto niespójny stan blokady budżetu wydatków");
        }
        escrowRepository.save(new JobExpenseEscrow(job, job.getCreatedBy(), budget, LocalDateTime.now()));
    }

    @Transactional
    public JobExpenseClaimResponse createClaim(Long jobId, CreateJobExpenseClaimRequest request, User currentUser) {
        Long workerId = currentUser == null ? null : currentUser.getId();
        if (workerId == null) {
            throw new ResourceNotFoundException("Zlecenie nie istnieje");
        }
        Job job = jobRepository.findAssignedWorkerByIdForUpdate(jobId, workerId)
                .orElseThrow(() -> new ResourceNotFoundException("Zlecenie nie istnieje"));
        if (job.getStatus() != JobStatus.IN_PROGRESS || job.getTakenBy() == null) {
            throw new ConflictException("Wydatek można zgłosić tylko podczas aktywnej realizacji zlecenia");
        }

        JobExpenseEscrow escrow = escrowRepository.findByJobIdForUpdate(jobId)
                .orElseThrow(() -> new ConflictException("To zlecenie nie ma budżetu na wydatki"));
        if (escrow.getStatus() != JobExpenseEscrowStatus.HELD) {
            throw new ConflictException("Budżet wydatków został już rozliczony");
        }

        JobAttachment receipt = attachmentRepository.findByIdAndJob_IdAndDeletedAtIsNull(request.attachmentId(), jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Załącznik-paragon nie istnieje"));
        if (receipt.getVisibility() != JobAttachmentVisibility.PARTICIPANTS
                || !sameUser(receipt.getUploadedBy(), currentUser)) {
            throw new ConflictException("Wydatek wymaga prywatnego załącznika PARTICIPANTS dodanego przez wykonawcę");
        }
        if (!RECEIPT_MEDIA_TYPES.contains(receipt.getMediaType())) {
            throw new ConflictException("Paragon musi być plikiem PDF, JPEG, PNG lub WebP");
        }
        if (claimRepository.existsByAttachment_Id(receipt.getId())) {
            throw new ConflictException("Ten załącznik został już użyty do zgłoszenia wydatku");
        }

        BigDecimal amount = normalizePositive(request.amount());
        BigDecimal remaining = escrow.getBudgetAmount().subtract(escrow.getClaimedAmount());
        if (amount.compareTo(remaining) > 0) {
            throw new BusinessException("Zgłoszone wydatki przekraczają pozostały budżet");
        }

        LocalDateTime now = LocalDateTime.now();
        escrow.addClaim(amount);
        escrowRepository.save(escrow);
        JobExpenseClaim saved = claimRepository.save(new JobExpenseClaim(job, currentUser, receipt, amount, now));
        return toClaimResponse(saved);
    }

    public JobExpenseSummaryResponse getSummary(Long jobId, User currentUser) {
        Long participantId = currentUser == null ? null : currentUser.getId();
        if (participantId == null) {
            throw new ResourceNotFoundException("Zlecenie nie istnieje");
        }
        jobRepository.findParticipantById(jobId, participantId)
                .orElseThrow(() -> new ResourceNotFoundException("Zlecenie nie istnieje"));
        return buildSummary(jobId);
    }

    public JobExpenseSummaryResponse getSummaryForAdmin(Long jobId, User admin) {
        if (admin == null || admin.getRole() != UserRole.ADMIN) {
            throw new ForbiddenOperationException("Tylko administrator może przeglądać wydatki jako dowody w sporze");
        }
        if (!jobRepository.existsById(jobId)) {
            throw new ResourceNotFoundException("Zlecenie nie istnieje");
        }
        return buildSummary(jobId);
    }

    private JobExpenseSummaryResponse buildSummary(Long jobId) {
        JobExpenseEscrow escrow = escrowRepository.findByJob_Id(jobId).orElse(null);
        if (escrow == null) {
            return new JobExpenseSummaryResponse(
                    jobId, ZERO, ZERO, ZERO, ZERO, null, null, null, List.of()
            );
        }
        List<JobExpenseClaimResponse> claims = claimRepository
                .findAllByJob_IdOrderByCreatedAtAscIdAsc(jobId)
                .stream()
                .map(this::toClaimResponse)
                .toList();
        return new JobExpenseSummaryResponse(
                jobId,
                escrow.getBudgetAmount(),
                escrow.getClaimedAmount(),
                escrow.getReimbursedAmount(),
                escrow.getRefundedAmount(),
                escrow.getStatus(),
                escrow.getHeldAt(),
                escrow.getResolvedAt(),
                claims
        );
    }

    @Transactional
    public void settleOnCompletion(Job job) {
        JobExpenseEscrow escrow = lockEscrow(job.getId());
        if (escrow == null) return;
        settle(job, escrow, escrow.getClaimedAmount());
    }

    @Transactional
    public void settleForDispute(Job job, BigDecimal approvedExpenseAmount) {
        BigDecimal approved = normalizeNonNegative(approvedExpenseAmount);
        JobExpenseEscrow escrow = lockEscrow(job.getId());
        if (escrow == null) {
            if (approved.signum() > 0) {
                throw new ConflictException("Nie można zatwierdzić wydatków dla zlecenia bez budżetu wydatków");
            }
            return;
        }
        if (approved.compareTo(escrow.getClaimedAmount()) > 0) {
            throw new ConflictException("Zatwierdzona kwota wydatków nie może przekraczać sumy zgłoszonych wydatków");
        }
        settle(job, escrow, approved);
    }

    private void settle(Job job, JobExpenseEscrow escrow, BigDecimal reimbursed) {
        if (escrow.getStatus() == JobExpenseEscrowStatus.SETTLED) return;
        if (escrow.getStatus() != JobExpenseEscrowStatus.HELD) {
            throw new ConflictException("Budżet wydatków został już zwrócony");
        }
        if (job.getTakenBy() == null) {
            throw new ConflictException("Nie można rozliczyć wydatków bez przypisanego wykonawcy");
        }

        BigDecimal refunded = escrow.getBudgetAmount().subtract(reimbursed).setScale(2, RoundingMode.UNNECESSARY);
        if (reimbursed.signum() > 0) {
            boolean reimbursedApplied = walletService.credit(
                    job.getTakenBy().getId(),
                    reimbursed,
                    WalletTransactionType.EXPENSE_REIMBURSEMENT,
                    job.getId(),
                    reimbursementOperationKey(job.getId())
            );
            if (!reimbursedApplied) {
                throw new ConflictException("Wykryto niespójny stan zwrotu wydatków wykonawcy");
            }
        }
        if (refunded.signum() > 0) {
            boolean refundApplied = walletService.creditRestoringJobDebits(
                    job.getCreatedBy().getId(),
                    refunded,
                    WalletTransactionType.EXPENSE_BUDGET_REFUND,
                    job.getId(),
                    refundOperationKey(job.getId()),
                    EXPENSE_SOURCE_DEBITS
            );
            if (!refundApplied) {
                throw new ConflictException("Wykryto niespójny stan zwrotu niewykorzystanego budżetu wydatków");
            }
        }
        escrow.settle(reimbursed, refunded, LocalDateTime.now());
        escrowRepository.save(escrow);
    }

    @Transactional
    public void refundAll(Job job) {
        JobExpenseEscrow escrow = lockEscrow(job.getId());
        if (escrow == null || escrow.getStatus() == JobExpenseEscrowStatus.REFUNDED) return;
        if (escrow.getStatus() != JobExpenseEscrowStatus.HELD) {
            throw new ConflictException("Budżet wydatków został już rozliczony z wykonawcą");
        }
        boolean refunded = walletService.creditRestoringJobDebits(
                job.getCreatedBy().getId(),
                escrow.getBudgetAmount(),
                WalletTransactionType.EXPENSE_BUDGET_REFUND,
                job.getId(),
                refundOperationKey(job.getId()),
                EXPENSE_SOURCE_DEBITS
        );
        if (!refunded) {
            throw new ConflictException("Wykryto niespójny stan zwrotu budżetu wydatków");
        }
        escrow.refundAll(LocalDateTime.now());
        escrowRepository.save(escrow);
    }

    @Transactional
    public void refundForMutualCancellation(Job job) {
        JobExpenseEscrow escrow = lockEscrow(job.getId());
        if (escrow == null || escrow.getStatus() == JobExpenseEscrowStatus.REFUNDED) return;
        if (escrow.getStatus() != JobExpenseEscrowStatus.HELD) {
            throw new ConflictException("Budżet wydatków został już rozliczony");
        }
        if (escrow.getClaimedAmount().signum() > 0) {
            throw new ConflictException("Zlecenie ma zgłoszone wydatki. Anulowanie wymaga rozstrzygnięcia sporu");
        }
        refundAll(job);
    }

    private JobExpenseEscrow lockEscrow(Long jobId) {
        return escrowRepository.findByJobIdForUpdate(jobId).orElse(null);
    }

    private BigDecimal normalizeBudget(BigDecimal amount) {
        if (amount == null) return ZERO;
        BigDecimal normalized;
        try {
            normalized = amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new BusinessException("Budżet wydatków może mieć maksymalnie dwa miejsca po przecinku");
        }
        if (normalized.signum() < 0 || normalized.compareTo(MAX_BUDGET) > 0) {
            throw new BusinessException("Budżet wydatków musi mieścić się w zakresie 0–10 000 PLN");
        }
        return normalized;
    }

    private BigDecimal normalizePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException("Kwota wydatku musi być dodatnia");
        }
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new BusinessException("Kwota wydatku może mieć maksymalnie dwa miejsca po przecinku");
        }
    }

    private BigDecimal normalizeNonNegative(BigDecimal amount) {
        if (amount == null) return ZERO;
        if (amount.signum() < 0) {
            throw new BusinessException("Zatwierdzona kwota wydatków nie może być ujemna");
        }
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new BusinessException("Zatwierdzona kwota wydatków może mieć maksymalnie dwa miejsca po przecinku");
        }
    }

    private boolean sameUser(User first, User second) {
        return first != null && second != null && first.getId() != null && first.getId().equals(second.getId());
    }

    private JobExpenseClaimResponse toClaimResponse(JobExpenseClaim claim) {
        return new JobExpenseClaimResponse(
                claim.getId(),
                claim.getAmount(),
                claim.getAttachment().getId(),
                claim.getWorker().getId(),
                claim.getCreatedAt()
        );
    }

    private String lockOperationKey(Long jobId) { return "job:" + jobId + ":expense:lock"; }
    private String reimbursementOperationKey(Long jobId) { return "job:" + jobId + ":expense:reimburse"; }
    private String refundOperationKey(Long jobId) { return "job:" + jobId + ":expense:refund"; }
}
