package com.doFast.dofastapp.verification.controller;

import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.verification.dto.VerificationResponse;
import com.doFast.dofastapp.verification.service.VerificationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/verification")
public class VerificationController {

    private final VerificationService verificationService;

    public VerificationController(VerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @GetMapping("/me")
    public VerificationResponse current(@AuthenticationPrincipal User user) {
        return verificationService.getCurrent(user);
    }

    @PostMapping("/request")
    public VerificationResponse request(@AuthenticationPrincipal User user) {
        return verificationService.requestVerification(user);
    }
}
