package com.doFast.dofastapp.user.service;

import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
import com.doFast.dofastapp.user.dto.AdminOverviewResponse;
import com.doFast.dofastapp.user.dto.AdminUserResponse;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.enums.UserRole;
import com.doFast.dofastapp.user.enums.UserStatus;
import com.doFast.dofastapp.user.repository.UserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class AdminUserService {

    private final UserRepository userRepository;

    public AdminUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
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
        return toResponse(userRepository.save(target));
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
}
