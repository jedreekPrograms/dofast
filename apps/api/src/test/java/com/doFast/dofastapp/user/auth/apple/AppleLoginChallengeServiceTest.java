package com.doFast.dofastapp.user.auth.apple;

import com.doFast.dofastapp.common.exception.AuthenticationFailedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppleLoginChallengeServiceTest {

    @Mock private AppleLoginChallengeRepository repository;

    @Test
    void challengeStoresOnlyHashesAndCanBeConsumedOnce() {
        AppleAuthConfiguration configuration = new AppleAuthConfiguration(
                "com.example.web",
                "https://example.com/auth/apple",
                "TEAM123",
                "KEY123",
                "configured-private-key",
                10
        );
        AppleLoginChallengeService service = new AppleLoginChallengeService(repository, configuration);
        when(repository.save(any(AppleLoginChallenge.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AppleLoginChallengeResponse response = service.createChallenge();
        ArgumentCaptor<AppleLoginChallenge> captor = ArgumentCaptor.forClass(AppleLoginChallenge.class);
        verify(repository).save(captor.capture());
        AppleLoginChallenge stored = captor.getValue();

        assertNotEquals(response.state(), stored.getStateHash());
        assertNotEquals(response.nonce(), stored.getNonceHash());
        assertNotNull(response.expiresAt());

        when(repository.findByIdForUpdate(response.challengeId())).thenReturn(Optional.of(stored));
        service.consume(response.challengeId(), response.state(), response.nonce());
        assertNotNull(stored.getConsumedAt());

        assertThrows(
                AuthenticationFailedException.class,
                () -> service.consume(response.challengeId(), response.state(), response.nonce())
        );
    }

    @Test
    void wrongStateCannotConsumeChallenge() {
        AppleAuthConfiguration configuration = new AppleAuthConfiguration(
                "com.example.web",
                "https://example.com/auth/apple",
                "TEAM123",
                "KEY123",
                "configured-private-key",
                10
        );
        AppleLoginChallengeService service = new AppleLoginChallengeService(repository, configuration);
        when(repository.save(any(AppleLoginChallenge.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AppleLoginChallengeResponse response = service.createChallenge();
        ArgumentCaptor<AppleLoginChallenge> captor = ArgumentCaptor.forClass(AppleLoginChallenge.class);
        verify(repository).save(captor.capture());
        when(repository.findByIdForUpdate(response.challengeId())).thenReturn(Optional.of(captor.getValue()));

        assertThrows(
                AuthenticationFailedException.class,
                () -> service.consume(response.challengeId(), "wrong-state", response.nonce())
        );
    }
}