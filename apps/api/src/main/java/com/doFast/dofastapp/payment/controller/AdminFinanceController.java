package com.doFast.dofastapp.payment.controller;

import com.doFast.dofastapp.payment.dto.FinanceReconciliationResponse;
import com.doFast.dofastapp.payment.service.FinanceReconciliationService;
import com.doFast.dofastapp.user.entity.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/finance")
public class AdminFinanceController {

    private final FinanceReconciliationService reconciliationService;

    public AdminFinanceController(FinanceReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @GetMapping("/reconciliation")
    public FinanceReconciliationResponse reconciliation(@AuthenticationPrincipal User admin) {
        return reconciliationService.reconcile(admin);
    }
}
