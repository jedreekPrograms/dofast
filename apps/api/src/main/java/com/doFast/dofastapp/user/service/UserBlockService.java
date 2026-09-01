package com.doFast.dofastapp.user.service;

import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
import com.doFast.dofastapp.user.dto.UserBlockResponse;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.entity.UserBlock;
import com.doFast.dofastapp.user.enums.UserStatus;
import com.doFast.dofastapp.user.repository.UserBlockRepository;
import com.doFast.dofastapp.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserBlockService {

    private final UserBlockRepository userBlockRepository;
    private final UserRepository userRepository;

    public UserBlockService(UserBlockRepository userBlockRepository, UserRepository userRepository) {
        this.userBlockRepository = userBlockRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public UserBlockResponse block(Long targetUserId, User blocker) {
        if (blocker.getId().equals(targetUserId)) {
            throw new ForbiddenOperationException("Nie możesz zablokować własnego konta");
        }

        User target = userRepository.findByIdAndStatus(targetUserId, UserStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Użytkownik nie istnieje"));

        return userBlockRepository.findByBlocker_IdAndBlockedUser_Id(blocker.getId(), targetUserId)
                .map(UserBlockResponse::from)
                .orElseGet(() -> UserBlockResponse.from(
                        userBlockRepository.save(new UserBlock(blocker, target))
                ));
    }

    @Transactional
    public void unblock(Long targetUserId, User blocker) {
        userBlockRepository.deleteByBlocker_IdAndBlockedUser_Id(blocker.getId(), targetUserId);
    }

    @Transactional(readOnly = true)
    public List<UserBlockResponse> mine(User blocker) {
        return userBlockRepository.findAllByBlocker_IdOrderByCreatedAtDesc(blocker.getId())
                .stream()
                .map(UserBlockResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean isInteractionBlocked(User first, User second) {
        return userBlockRepository.existsByBlocker_IdAndBlockedUser_IdOrBlocker_IdAndBlockedUser_Id(
                first.getId(),
                second.getId(),
                second.getId(),
                first.getId()
        );
    }
}
