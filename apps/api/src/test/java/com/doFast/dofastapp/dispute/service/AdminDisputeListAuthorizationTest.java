package com.doFast.dofastapp.dispute.service;

import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.dispute.repository.DisputeEventRepository;
import com.doFast.dofastapp.dispute.repository.DisputeRepository;
import com.doFast.dofastapp.job.expense.JobExpenseService;
import com.doFast.dofastapp.job.repository.JobRepository;
import com.doFast.dofastapp.notification.service.NotificationService;
import com.doFast.dofastapp.payment.service.TransactionService;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.enums.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDisputeListAuthorizationTest {

    @Mock private DisputeRepository disputeRepository;
    @Mock private DisputeEventRepository eventRepository;
    @Mock private JobRepository jobRepository;
    @Mock private TransactionService transactionService;
    @Mock private NotificationService notificationService;
    @Mock private JobExpenseService expenseService;

    private DisputeService disputeService;

    @BeforeEach
    void setUp() {
        disputeService = new DisputeService(
                disputeRepository,
                eventRepository,
                jobRepository,
                transactionService,
                notificationService,
                expenseService
        );
    }

    @Test
    void regularUserCannotReadAdminDisputeQueueEvenWhenServiceIsCalledDirectly() {
        User user = new User("user@example.com", "user");
        user.setRole(UserRole.USER);

        assertThrows(ForbiddenOperationException.class,
                () -> disputeService.getAdminDisputes(null, 0, 20, user));

        verify(disputeRepository, never()).findAll(any(Pageable.class));
        verify(disputeRepository, never()).findByStatus(any(), any(Pageable.class));
    }

    @Test
    void missingPrincipalCannotReadAdminDisputeQueue() {
        assertThrows(ForbiddenOperationException.class,
                () -> disputeService.getAdminDisputes(null, 0, 20, null));

        verify(disputeRepository, never()).findAll(any(Pageable.class));
        verify(disputeRepository, never()).findByStatus(any(), any(Pageable.class));
    }

    @Test
    void administratorCanReadAdminDisputeQueue() {
        User admin = new User("admin@example.com", "admin");
        admin.setRole(UserRole.ADMIN);
        when(disputeRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());

        assertDoesNotThrow(() -> disputeService.getAdminDisputes(null, 0, 20, admin));

        verify(disputeRepository).findAll(any(Pageable.class));
    }
}
