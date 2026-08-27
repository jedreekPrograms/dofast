package com.doFast.dofastapp.user.service;

import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
import com.doFast.dofastapp.user.dto.AdminOverviewResponse;
import com.doFast.dofastapp.user.dto.AdminUserReactivationAuditResponse;
import com.doFast.dofastapp.user.dto.AdminUserResponse;
import com.doFast.dofastapp.user.entity.AdminUserReactivationAudit;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.enums.UserRole;
import com.doFast.dofastapp.user.enums.UserStatus;
import com.doFast.dofastapp.user.repository.AdminUserReactivationAuditRepository;
import com.doFast.dofastapp.user.repository.UserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class AdminUserService {

    private final UserRepository userRepository;
    private final AdminUserReactivationAuditRepository reactivationAuditRepository;

    public AdminUserService(
            UserRepository userRepository,
            AdminUserReactivationAuditRepository reactivationAuditRepository
    ) {
        this.userRepository = userRepository;
        this.reactivationAuditRepository = reactivationAuditRepository;
    }

    public AdminOverviewResponse getOverview() {
        return new AdminOverviewResponse(
                userRepository.count(),
                userRepository.countByStatus(UserStatus.ACTIVE),
                userRepository.countByStatus(UserStatus.SUSPENDED)
        );
    }

    public List<AdminUserResponse> getUsers() {
        return userRepository.findAll(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<AdminUserReactivationAuditResponse> getReactivationHistory(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("Użytkownik nie istnieje");
        }
        return reactivationAuditRepository.findAllByUser_IdOrderByCreatedAtDescIdDesc(userId)
                .stream()
                .map(this::toReactivationResponse)
                .toList();
    }

    @Transactional
    public AdminUserResponse updateStatus(Long userId, UserStatus status, User currentAdmin) {
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Użytkownik nie istnieje"));

        if (status == UserStatus.SUSPENDED) {
            throw new ForbiddenOperationException(
                    "Zawieszenie konta wymaga audytowalnej akcji egzekucyjnej z potwierdzonego zgłoszenia"
            );
        }
        if (target.getRole() == UserRole.ADMIN && !target.getId().equals(currentAdmin.getId())) {
            throw new ForbiddenOperationException("Status innego administratora nie może być zmieniony z tego panelu");
        }
        if (target.getStatus() != UserStatus.SUSPENDED) {
            throw new ForbiddenOperationException("Tylko zawieszone konto może zostać ponownie aktywowane");
        }

        target.setStatus(UserStatus.ACTIVE);
        User saved = userRepository.save(target);
        reactivationAuditRepository.save(new AdminUserReactivationAudit(saved, currentAdmin));
        return toResponse(saved);
    }

    private AdminUserResponse toResponse(User user) {
        return new AdminUserResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getRole(),
                user.getStatus(),
                user.getCreatedAt()
        );
    }

    private AdminUserReactivationAuditResponse toReactivationResponse(AdminUserReactivationAudit audit) {
        User admin = audit.getAdmin();
        return new AdminUserReactivationAuditResponse(
                audit.getId(),
                audit.getUser().getId(),
                admin.getId(),
                admin.getEmail(),
                admin.getNickname(),
                audit.getPreviousStatus(),
                audit.getNewStatus(),
                audit.getCreatedAt()
        );
    }
}
