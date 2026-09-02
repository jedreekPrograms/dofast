package com.doFast.dofastapp.wallet.controller;

import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.wallet.service.WalletHistoryService;
import com.doFast.dofastapp.wallet.service.WalletService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class WalletControllerTest {

    @Mock
    private WalletService walletService;

    @Mock
    private WalletHistoryService walletHistoryService;

    @Test
    void walletReadsFailClosedBeforeFinancialStateAccessWithoutAuthenticatedIdentity() {
        WalletController controller = new WalletController(walletService, walletHistoryService);
        User transientUser = new User();

        assertThrows(ForbiddenOperationException.class, () -> controller.getMyWallet(null));
        assertThrows(ForbiddenOperationException.class, () -> controller.getMyWallet(transientUser));
        assertThrows(ForbiddenOperationException.class, () -> controller.history(null));
        assertThrows(ForbiddenOperationException.class, () -> controller.history(transientUser));

        verifyNoInteractions(walletService, walletHistoryService);
    }
}
