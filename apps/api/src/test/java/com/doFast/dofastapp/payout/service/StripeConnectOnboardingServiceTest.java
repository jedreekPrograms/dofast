package com.doFast.dofastapp.payout.service;

import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.payout.config.StripeConnectProperties;
import com.doFast.dofastapp.payout.entity.PayoutRecipientAccount;
import com.doFast.dofastapp.payout.provider.StripeConnectAccountState;
import com.doFast.dofastapp.payout.provider.StripeConnectGateway;
import com.doFast.dofastapp.payout.repository.PayoutRecipientAccountRepository;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.enums.UserStatus;
import com.doFast.dofastapp.user.repository.UserRepository;
import com.doFast.dofastapp.verification.enums.VerificationStatus;
import com.doFast.dofastapp.verification.repository.VerificationCaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StripeConnectOnboardingServiceTest {

    @Mock private PayoutRecipientAccountRepository repository;
    @Mock private StripeConnectGateway gateway;
    @Mock private VerificationCaseRepository verificationRepository;
    @Mock private UserRepository userRepository;

    private StripeConnectOnboardingService service;

    @BeforeEach
    void setUp() {
        service = new StripeConnectOnboardingService(
                new StripeConnectProperties(
                        true,
                        "PL",
                        "https://app.dofast.pl/wallet?stripe-connect=refresh",
                        "https://app.dofast.pl/wallet?stripe-connect=return"
                ),
                repository,
                gateway,
                verificationRepository,
                userRepository
        );
    }

    @Test
    void verifiedUserGetsPersistentIdempotentExpressAccountAndHostedLink() {
        User user = user(7L, UserStatus.ACTIVE);
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        when(verificationRepository.existsByUser_IdAndStatus(7L, VerificationStatus.VERIFIED)).thenReturn(true);
        when(repository.findForUpdate(7L, StripeConnectOnboardingService.PROVIDER_CODE)).thenReturn(Optional.empty());
        when(gateway.createExpressAccount(user, "PL", "dofast:stripe-connect:user:7")).thenReturn("acct_123");
        when(repository.saveAndFlush(any(PayoutRecipientAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(gateway.createOnboardingLink(
                "acct_123",
                "https://app.dofast.pl/wallet?stripe-connect=refresh",
                "https://app.dofast.pl/wallet?stripe-connect=return"
        )).thenReturn("https://connect.stripe.com/setup/123");

        var response = service.createOnboardingLink(user);

        assertEquals("https://connect.stripe.com/setup/123", response.url());
        verify(repository).saveAndFlush(any(PayoutRecipientAccount.class));
    }

    @Test
    void unverifiedUserCannotProvisionExternalStripeAccount() {
        User user = user(7L, UserStatus.ACTIVE);
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        when(verificationRepository.existsByUser_IdAndStatus(7L, VerificationStatus.VERIFIED)).thenReturn(false);

        assertThrows(ForbiddenOperationException.class, () -> service.createOnboardingLink(user));

        verify(gateway, never()).createExpressAccount(any(), any(), any());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void freshlyLockedSuspendedUserCannotProvisionExternalStripeAccount() {
        User principal = user(7L, UserStatus.ACTIVE);
        User suspended = user(7L, UserStatus.SUSPENDED);
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(suspended));

        assertThrows(ForbiddenOperationException.class, () -> service.createOnboardingLink(principal));

        verify(verificationRepository, never()).existsByUser_IdAndStatus(any(), any());
        verify(gateway, never()).createExpressAccount(any(), any(), any());
    }

    @Test
    void refreshPersistsAuthoritativeProviderReadiness() {
        User user = user(7L, UserStatus.ACTIVE);
        PayoutRecipientAccount account = new PayoutRecipientAccount();
        account.initialize(user, StripeConnectOnboardingService.PROVIDER_CODE, "acct_123", java.time.LocalDateTime.now().minusDays(1));
        when(repository.findForUpdate(7L, StripeConnectOnboardingService.PROVIDER_CODE)).thenReturn(Optional.of(account));
        when(gateway.retrieveState("acct_123")).thenReturn(new StripeConnectAccountState(true, true, true, false));
        when(repository.save(account)).thenReturn(account);

        var response = service.refreshStatus(user);

        assertTrue(response.readyForPayout());
        assertTrue(response.detailsSubmitted());
        assertTrue(response.payoutsEnabled());
        assertTrue(response.transfersEnabled());
        assertFalse(response.requirementsDue());
        verify(repository).save(account);
    }

    @Test
    void missingPrincipalFailsClosedBeforeFinancialStateAccess() {
        assertThrows(ForbiddenOperationException.class, () -> service.cachedStatus(null));
        assertThrows(ForbiddenOperationException.class, () -> service.refreshStatus(null));
        assertThrows(ForbiddenOperationException.class, () -> service.refreshAndIsRecipientReady(null));
        assertThrows(ForbiddenOperationException.class, () -> service.createOnboardingLink(null));

        verifyNoInteractions(repository, gateway, verificationRepository, userRepository);
    }

    @Test
    void transientPrincipalFailsClosedBeforeFinancialStateAccess() {
        User transientUser = user(null, UserStatus.ACTIVE);

        assertThrows(ForbiddenOperationException.class, () -> service.cachedStatus(transientUser));
        assertThrows(ForbiddenOperationException.class, () -> service.refreshStatus(transientUser));
        assertThrows(ForbiddenOperationException.class, () -> service.refreshAndIsRecipientReady(transientUser));
        assertThrows(ForbiddenOperationException.class, () -> service.createOnboardingLink(transientUser));

        verifyNoInteractions(repository, gateway, verificationRepository, userRepository);
    }

    private User user(Long id, UserStatus status) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setEmail("user" + id + "@example.com");
        user.setNickname("user" + id);
        user.setPassword("hash");
        user.setStatus(status);
        return user;
    }
}
