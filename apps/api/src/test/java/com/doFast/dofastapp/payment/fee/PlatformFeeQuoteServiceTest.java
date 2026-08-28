package com.doFast.dofastapp.payment.fee;

import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.repository.JobRepository;
import com.doFast.dofastapp.job.service.JobVisibilityService;
import com.doFast.dofastapp.payment.entity.Transaction;
import com.doFast.dofastapp.payment.repository.TransactionRepository;
import com.doFast.dofastapp.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformFeeQuoteServiceTest {

    @Mock private JobRepository jobRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private JobVisibilityService jobVisibilityService;
    @Mock private Job job;
    @Mock private Transaction transaction;
    @Mock private User viewer;

    @Test
    void existingJobQuoteUsesEscrowSnapshotAndVisibilityPolicy() {
        PlatformFeeQuoteService service = new PlatformFeeQuoteService(
                jobRepository,
                transactionRepository,
                new PlatformFeePolicy(300),
                jobVisibilityService
        );
        when(jobRepository.findById(91L)).thenReturn(Optional.of(job));
        when(transactionRepository.findByJob(job)).thenReturn(Optional.of(transaction));
        when(transaction.getPlatformFeeBasisPoints()).thenReturn(100);

        PlatformFeeQuote quote = service.quoteForJob(91L, new BigDecimal("50.00"), viewer);

        verify(jobVisibilityService).assertCanViewPublicDetail(91L, viewer);
        assertEquals(100, quote.basisPoints());
        assertEquals(new BigDecimal("0.50"), quote.platformFeeAmount());
        assertEquals(new BigDecimal("49.50"), quote.workerPayoutAmount());
    }
}
