package com.doFast.dofastapp.wallet.controller;

import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.wallet.dto.WalletResponse;
import com.doFast.dofastapp.wallet.dto.WalletTransactionResponse;
import com.doFast.dofastapp.wallet.service.WalletHistoryService;
import com.doFast.dofastapp.wallet.service.WalletService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/wallet")
public class WalletController {

    private final WalletService walletService;
    private final WalletHistoryService walletHistoryService;

    public WalletController(WalletService walletService, WalletHistoryService walletHistoryService) {
        this.walletService = walletService;
        this.walletHistoryService = walletHistoryService;
    }

    @GetMapping
    public WalletResponse getMyWallet() {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        User user = (User) authentication.getPrincipal();

        return walletService.getMyWallet(user.getId());
    }

    @GetMapping("/transactions")
    public List<WalletTransactionResponse> history() {

        User user = (User) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();

        return walletHistoryService.getHistory(user);
    }
}
