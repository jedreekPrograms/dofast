package com.doFast.dofastapp.user.controller;

import com.doFast.dofastapp.user.dto.AdminOverviewResponse;
import com.doFast.dofastapp.user.dto.AdminUserReactivationAuditResponse;
import com.doFast.dofastapp.user.dto.AdminUserResponse;
import com.doFast.dofastapp.user.dto.UpdateUserStatusRequest;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.service.AdminUserService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping("/overview")
    public AdminOverviewResponse overview() {
        return adminUserService.getOverview();
    }

    @GetMapping("/users")
    public List<AdminUserResponse> users() {
        return adminUserService.getUsers();
    }

    @GetMapping("/users/{id}/reactivation-audits")
    public List<AdminUserReactivationAuditResponse> reactivationAudits(@PathVariable Long id) {
        return adminUserService.getReactivationHistory(id);
    }

    @PatchMapping("/users/{id}/status")
    public AdminUserResponse updateStatus(
            @PathVariable Long id,
            @AuthenticationPrincipal User admin,
            @RequestBody @Valid UpdateUserStatusRequest request
    ) {
        return adminUserService.updateStatus(id, request.status(), admin);
    }
}
