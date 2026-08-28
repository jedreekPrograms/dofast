package com.doFast.dofastapp.payment.fee;

import com.doFast.dofastapp.common.exception.BusinessException;
import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.repository.JobRepository;
import com.doFast.dofastapp.job.service.JobVisibilityService;
import com.doFast.dofastapp.payment.entity.Transaction;
import com.doFast.dofastapp.payment.repository.TransactionRepository;
import com.doFast.dofastapp.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@Transactional(readOnly = true)
public class PlatformFeeQuoteService {

    private final JobRepository jobRepository;
    private final TransactionRepository transactionRepository;
    private final PlatformFeePolicy platformFeePolicy;
    private final JobVisibilityService jobVisibilityService;

    public PlatformFeeQuoteService(
            JobRepository jobRepository,
            TransactionRepository transactionRepository,
            PlatformFeePolicy platformFeePolicy,
            JobVisibilityService jobVisibilityService
    ) {
        this.jobRepository = jobRepository;
        this.transactionRepository = transactionRepository;
        this.platformFeePolicy = platformFeePolicy;
        this.jobVisibilityService = jobVisibilityService;
    }

    public PlatformFeeQuote quoteCurrent(BigDecimal amount) {
        return platformFeePolicy.quoteCurrent(amount);
    }

    public PlatformFeeQuote quoteForJob(Long jobId, BigDecimal amount, User currentUser) {
        jobVisibilityService.assertCanViewPublicDetail(jobId, currentUser);
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Zlecenie nie istnieje"));
        Transaction transaction = transactionRepository.findByJob(job)
                .orElseThrow(() -> new BusinessException("Brak transakcji escrow"));
        return platformFeePolicy.quote(amount, transaction.getPlatformFeeBasisPoints());
    }
}
