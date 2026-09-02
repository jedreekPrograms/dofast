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
        Long blockerId = requireUserId(blocker);
        if (blockerId.equals(targetUserId)) {
            throw new ForbiddenOperationException("Nie możesz zablokować własnego konta");
        }

        User target = userRepository.findByIdAndStatus(targetUserId, UserStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Użytkownik nie istnieje"));

        return userBlockRepository.findByBlocker_IdAndBlockedUser_Id(blockerId, targetUserId)
                .map(UserBlockResponse::from)
                .orElseGet(() -> UserBlockResponse.from(
                        userBlockRepository.save(new UserBlock(blocker, target))
                ));
    }

    @Transactional
    public void unblock(Long targetUserId, User blocker) {
        Long blockerId = requireUserId(blocker);
        userBlockRepository.deleteByBlocker_IdAndBlockedUser_Id(blockerId, targetUserId);
    }

    @Transactional(readOnly = true)
    public List<UserBlockResponse> mine(User blocker) {
        Long blockerId = requireUserId(blocker);
        return userBlockRepository.findAllByBlocker_IdOrderByCreatedAtDesc(blockerId)
                .stream()
                .map(UserBlockResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean isInteractionBlocked(User first, User second) {
        Long firstId = requireUserId(first);
        Long secondId = requireUserId(second);
        return userBlockRepository.existsByBlocker_IdAndBlockedUser_IdOrBlocker_IdAndBlockedUser_Id(
                firstId,
                secondId,
                secondId,
                firstId
        );
    }

    private Long requireUserId(User user) {
        if (user == null || user.getId() == null) {
            throw new ForbiddenOperationException("Zaloguj się, aby korzystać z blokowania użytkowników");
        }
        return user.getId();
    }
}
