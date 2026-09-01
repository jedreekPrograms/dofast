package com.doFast.dofastapp.user.service;

import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.entity.UserBlock;
import com.doFast.dofastapp.user.enums.UserStatus;
import com.doFast.dofastapp.user.repository.UserBlockRepository;
import com.doFast.dofastapp.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserBlockServiceTest {

    @Mock private UserBlockRepository userBlockRepository;
    @Mock private UserRepository userRepository;

    private UserBlockService service;
    private User blocker;
    private User target;

    @BeforeEach
    void setUp() {
        service = new UserBlockService(userBlockRepository, userRepository);
        blocker = user(1L, "blocker");
        target = user(2L, "target");
    }

    @Test
    void blockCreatesPrivateRelationForActiveTargetAndReturnsOnlyPublicIdentity() {
        when(userRepository.findByIdAndStatus(2L, UserStatus.ACTIVE)).thenReturn(Optional.of(target));
        when(userBlockRepository.findByBlocker_IdAndBlockedUser_Id(1L, 2L))
                .thenReturn(Optional.empty());
        when(userBlockRepository.save(any(UserBlock.class))).thenAnswer(invocation -> {
            UserBlock block = invocation.getArgument(0);
            ReflectionTestUtils.setField(block, "id", 10L);
            ReflectionTestUtils.setField(block, "createdAt", LocalDateTime.of(2026, 8, 28, 1, 0));
            return block;
        });

        var response = service.block(2L, blocker);

        assertEquals(2L, response.userId());
        assertEquals("target", response.nickname());
        verify(userRepository).findByIdAndStatus(2L, UserStatus.ACTIVE);
        verify(userRepository, never()).findById(any());
        verify(userBlockRepository).save(any(UserBlock.class));
    }

    @Test
    void blockIsIdempotentForActiveTarget() {
        UserBlock existing = block(blocker, target, LocalDateTime.of(2026, 8, 28, 1, 0));
        when(userRepository.findByIdAndStatus(2L, UserStatus.ACTIVE)).thenReturn(Optional.of(target));
        when(userBlockRepository.findByBlocker_IdAndBlockedUser_Id(1L, 2L))
                .thenReturn(Optional.of(existing));

        var response = service.block(2L, blocker);

        assertEquals(2L, response.userId());
        verify(userRepository).findByIdAndStatus(2L, UserStatus.ACTIVE);
        verify(userRepository, never()).findById(any());
        verify(userBlockRepository, never()).save(any());
    }

    @Test
    void selfBlockIsRejectedBeforeTargetLookup() {
        assertThrows(ForbiddenOperationException.class, () -> service.block(1L, blocker));
        verify(userRepository, never()).findByIdAndStatus(any(), any());
        verify(userRepository, never()).findById(any());
        verify(userBlockRepository, never()).findByBlocker_IdAndBlockedUser_Id(any(), any());
        verify(userBlockRepository, never()).save(any());
    }

    @Test
    void suspendedOrUnknownTargetIsRejectedBeforeBlockRelationLookup() {
        when(userRepository.findByIdAndStatus(999L, UserStatus.ACTIVE)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.block(999L, blocker));

        verify(userRepository).findByIdAndStatus(999L, UserStatus.ACTIVE);
        verify(userRepository, never()).findById(any());
        verify(userBlockRepository, never()).findByBlocker_IdAndBlockedUser_Id(any(), any());
        verify(userBlockRepository, never()).save(any());
    }

    @Test
    void mineReturnsOnlyCurrentUsersBlockList() {
        UserBlock existing = block(blocker, target, LocalDateTime.of(2026, 8, 28, 1, 0));
        when(userBlockRepository.findAllByBlocker_IdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(existing));

        var result = service.mine(blocker);

        assertEquals(1, result.size());
        assertEquals(2L, result.getFirst().userId());
    }

    @Test
    void interactionIsBlockedInEitherDirection() {
        when(userBlockRepository.existsByBlocker_IdAndBlockedUser_IdOrBlocker_IdAndBlockedUser_Id(
                1L, 2L, 2L, 1L
        )).thenReturn(true);

        assertTrue(service.isInteractionBlocked(blocker, target));
    }

    private User user(Long id, String nickname) {
        User user = new User(nickname + "@example.com", nickname);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private UserBlock block(User blocker, User blockedUser, LocalDateTime createdAt) {
        UserBlock block = new UserBlock(blocker, blockedUser);
        ReflectionTestUtils.setField(block, "createdAt", createdAt);
        return block;
    }
}
