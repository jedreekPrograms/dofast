package com.doFast.dofastapp.user.dto;

import com.doFast.dofastapp.user.entity.UserBlock;

import java.time.LocalDateTime;

public record UserBlockResponse(
        Long userId,
        String nickname,
        LocalDateTime blockedAt
) {
    public static UserBlockResponse from(UserBlock block) {
        return new UserBlockResponse(
                block.getBlockedUser().getId(),
                block.getBlockedUser().getNickname(),
                block.getCreatedAt()
        );
    }
}
