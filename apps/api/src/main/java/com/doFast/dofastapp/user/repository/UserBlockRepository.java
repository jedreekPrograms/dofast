package com.doFast.dofastapp.user.repository;

import com.doFast.dofastapp.user.entity.UserBlock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {
    Optional<UserBlock> findByBlocker_IdAndBlockedUser_Id(Long blockerId, Long blockedUserId);
    boolean existsByBlocker_IdAndBlockedUser_Id(Long blockerId, Long blockedUserId);
    boolean existsByBlocker_IdAndBlockedUser_IdOrBlocker_IdAndBlockedUser_Id(
            Long firstBlockerId,
            Long firstBlockedUserId,
            Long secondBlockerId,
            Long secondBlockedUserId
    );
    List<UserBlock> findAllByBlocker_IdOrderByCreatedAtDesc(Long blockerId);
    long deleteByBlocker_IdAndBlockedUser_Id(Long blockerId, Long blockedUserId);
}
