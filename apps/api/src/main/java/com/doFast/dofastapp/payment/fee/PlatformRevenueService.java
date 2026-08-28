package com.doFast.dofastapp.payment.fee;

import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.payment.entity.Transaction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Transactional
public class PlatformRevenueService {

    private final PlatformRevenueEntryRepository repository;

    public PlatformRevenueService(PlatformRevenueEntryRepository repository) {
        this.repository = repository;
    }

    public void recordPlatformFee(Transaction transaction, Job job, BigDecimal amount) {
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("Platform fee amount cannot be negative");
        }
        if (amount.signum() == 0) {
            return;
        }
        if (transaction.getId() == null || job.getId() == null) {
            throw new IllegalArgumentException("Persisted escrow transaction and job are required");
        }

        String operationKey = "platform-fee:job:" + job.getId() + ":release";
        Optional<PlatformRevenueEntry> existing = repository.findByOperationKey(operationKey);
        if (existing.isPresent()) {
            PlatformRevenueEntry entry = existing.get();
            boolean sameTransaction = entry.getEscrowTransaction().getId().equals(transaction.getId());
            boolean sameJob = entry.getJob().getId().equals(job.getId());
            boolean sameAmount = entry.getAmount().compareTo(amount) == 0;
            if (sameTransaction && sameJob && sameAmount && entry.getType() == PlatformRevenueType.PLATFORM_FEE) {
                return;
            }
            throw new ConflictException("Klucz przychodu platformy został już użyty dla innego rozliczenia");
        }

        repository.save(new PlatformRevenueEntry(
                transaction,
                job,
                PlatformRevenueType.PLATFORM_FEE,
                amount,
                operationKey,
                LocalDateTime.now()
        ));
    }
}
