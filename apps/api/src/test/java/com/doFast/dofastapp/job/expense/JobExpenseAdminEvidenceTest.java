package com.doFast.dofastapp.job.expense;

import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.job.attachment.JobAttachmentRepository;
import com.doFast.dofastapp.job.repository.JobRepository;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.enums.UserRole;
import com.doFast.dofastapp.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobExpenseAdminEvidenceTest {

    @Mock private JobRepository jobRepository;
    @Mock private JobExpenseEscrowRepository escrowRepository;
    @Mock private JobExpenseClaimRepository claimRepository;
    @Mock private JobAttachmentRepository attachmentRepository;
    @Mock private WalletService walletService;

    private JobExpenseService service;

    @BeforeEach
    void setUp() {
        service = new JobExpenseService(
                jobRepository,
                escrowRepository,
                claimRepository,
                attachmentRepository,
                walletService
        );
    }

    @Test
    void adminCanReadEmptyExpenseEvidenceWithoutBeingJobParticipant() {
        User admin = user(10L, UserRole.ADMIN);
        when(jobRepository.existsById(50L)).thenReturn(true);
        when(escrowRepository.findByJob_Id(50L)).thenReturn(java.util.Optional.empty());

        JobExpenseSummaryResponse summary = service.getSummaryForAdmin(50L, admin);

        assertEquals(50L, summary.jobId());
        assertEquals(new BigDecimal("0.00"), summary.budgetAmount());
        assertEquals(new BigDecimal("0.00"), summary.claimedAmount());
        assertEquals(0, summary.claims().size());
    }

    @Test
    void transientAdminCannotUseAdminEvidencePath() {
        User transientAdmin = new User("transient-admin@example.com", "transient-admin");
        transientAdmin.setRole(UserRole.ADMIN);

        assertThrows(ForbiddenOperationException.class,
                () -> service.getSummaryForAdmin(50L, transientAdmin));

        verifyNoInteractions(
                jobRepository,
                escrowRepository,
                claimRepository,
                attachmentRepository,
                walletService
        );
    }

    @Test
    void ordinaryUserCannotUseAdminEvidencePath() {
        User user = user(2L, UserRole.USER);

        assertThrows(ForbiddenOperationException.class, () -> service.getSummaryForAdmin(50L, user));

        verify(jobRepository, never()).existsById(50L);
        verify(escrowRepository, never()).findByJob_Id(50L);
    }

    private User user(Long id, UserRole role) {
        User user = new User("user" + id + "@example.com", "user" + id);
        user.setRole(role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
