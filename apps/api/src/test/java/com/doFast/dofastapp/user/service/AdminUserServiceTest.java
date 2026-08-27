package com.doFast.dofastapp.user.service;

import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.user.dto.AdminUserReactivationAuditResponse;
import com.doFast.dofastapp.user.dto.AdminUserResponse;
import com.doFast.dofastapp.user.entity.AdminUserReactivationAudit;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.enums.UserRole;
import com.doFast.dofastapp.user.enums.UserStatus;
import com.doFast.dofastapp.user.repository.AdminUserReactivationAuditRepository;
import com.doFast.dofastapp.user.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminUserServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final AdminUserReactivationAuditRepository reactivationAuditRepository =
            mock(AdminUserReactivationAuditRepository.class);
    private final AdminUserService service = new AdminUserService(userRepository, reactivationAuditRepository);

    @Test
    void genericStatusEndpointCannotSuspendUser() {
        User target = mock(User.class);
        User admin = mock(User.class);
        when(userRepository.findById(7L)).thenReturn(Optional.of(target));

        assertThrows(
                ForbiddenOperationException.class,
                () -> service.updateStatus(7L, UserStatus.SUSPENDED, admin)
        );

        verify(target, never()).setStatus(UserStatus.SUSPENDED);
        verify(userRepository, never()).save(target);
        verify(reactivationAuditRepository, never()).save(any());
    }

    @Test
    void suspendedUserCanBeReactivatedAndAudited() {
        User target = mock(User.class);
        User admin = mock(User.class);
        LocalDateTime createdAt = LocalDateTime.of(2026, 8, 27, 12, 0);

        when(userRepository.findById(8L)).thenReturn(Optional.of(target));
        when(target.getId()).thenReturn(8L);
        when(target.getEmail()).thenReturn("user@example.com");
        when(target.getNickname()).thenReturn("user");
        when(target.getRole()).thenReturn(UserRole.USER);
        when(target.getStatus()).thenReturn(UserStatus.SUSPENDED, UserStatus.ACTIVE);
        when(target.getCreatedAt()).thenReturn(createdAt);
        when(userRepository.save(target)).thenAnswer(invocation -> target);

        AdminUserResponse response = service.updateStatus(8L, UserStatus.ACTIVE, admin);

        verify(target).setStatus(UserStatus.ACTIVE);
        verify(userRepository).save(target);
        verify(reactivationAuditRepository).save(any(AdminUserReactivationAudit.class));
        assertEquals(UserStatus.ACTIVE, response.status());
    }

    @Test
    void reactivationHistoryIsReturnedNewestFirstWithActorIdentity() {
        User target = mock(User.class);
        User admin = mock(User.class);
        AdminUserReactivationAudit audit = mock(AdminUserReactivationAudit.class);
        LocalDateTime createdAt = LocalDateTime.of(2026, 8, 27, 14, 30);

        when(userRepository.existsById(8L)).thenReturn(true);
        when(reactivationAuditRepository.findAllByUser_IdOrderByCreatedAtDescIdDesc(8L))
                .thenReturn(List.of(audit));
        when(audit.getId()).thenReturn(12L);
        when(audit.getUser()).thenReturn(target);
        when(audit.getAdmin()).thenReturn(admin);
        when(audit.getPreviousStatus()).thenReturn(UserStatus.SUSPENDED);
        when(audit.getNewStatus()).thenReturn(UserStatus.ACTIVE);
        when(audit.getCreatedAt()).thenReturn(createdAt);
        when(target.getId()).thenReturn(8L);
        when(admin.getId()).thenReturn(2L);
        when(admin.getEmail()).thenReturn("admin@example.com");
        when(admin.getNickname()).thenReturn("moderator");

        List<AdminUserReactivationAuditResponse> response = service.getReactivationHistory(8L);

        assertEquals(1, response.size());
        assertEquals(12L, response.getFirst().id());
        assertEquals(8L, response.getFirst().userId());
        assertEquals(2L, response.getFirst().adminId());
        assertEquals("admin@example.com", response.getFirst().adminEmail());
        assertEquals(UserStatus.SUSPENDED, response.getFirst().previousStatus());
        assertEquals(UserStatus.ACTIVE, response.getFirst().newStatus());
        assertEquals(createdAt, response.getFirst().createdAt());
    }

    @Test
    void activeUserCannotUseReactivationEndpointAsNoOp() {
        User target = mock(User.class);
        User admin = mock(User.class);
        when(userRepository.findById(9L)).thenReturn(Optional.of(target));
        when(target.getRole()).thenReturn(UserRole.USER);
        when(target.getStatus()).thenReturn(UserStatus.ACTIVE);

        assertThrows(
                ForbiddenOperationException.class,
                () -> service.updateStatus(9L, UserStatus.ACTIVE, admin)
        );

        verify(userRepository, never()).save(target);
        verify(reactivationAuditRepository, never()).save(any());
    }
}
