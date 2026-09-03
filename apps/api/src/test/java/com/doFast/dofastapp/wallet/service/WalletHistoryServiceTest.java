package com.doFast.dofastapp.wallet.service;

import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.wallet.entity.Wallet;
import com.doFast.dofastapp.wallet.repository.WalletRepository;
import com.doFast.dofastapp.wallet.repository.WalletTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletHistoryServiceTest {

    @Mock private WalletRepository walletRepository;
    @Mock private WalletTransactionRepository transactionRepository;

    @Test
    void historyFailsClosedBeforeWalletLookupWithoutPersistedIdentity() {
        WalletHistoryService service = service();
        User transientUser = new User("transient@example.com", "transient");

        assertThrows(ForbiddenOperationException.class, () -> service.getHistory(null));
        assertThrows(ForbiddenOperationException.class, () -> service.getHistory(transientUser));

        verifyNoInteractions(walletRepository, transactionRepository);
    }

    @Test
    void persistedUserCanReadEmptyWalletHistory() {
        User user = new User("user@example.com", "user");
        ReflectionTestUtils.setField(user, "id", 7L);
        Wallet wallet = new Wallet(user);
        when(walletRepository.findByUser(user)).thenReturn(Optional.of(wallet));
        when(transactionRepository.findByWalletOrderByCreatedAtDescIdDesc(wallet)).thenReturn(List.of());

        assertEquals(List.of(), service().getHistory(user));
    }

    private WalletHistoryService service() {
        return new WalletHistoryService(walletRepository, transactionRepository);
    }
}
