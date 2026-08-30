package com.doFast.dofastapp.user.controller;

import com.doFast.dofastapp.user.auth.email.EmailVerificationService;
import com.doFast.dofastapp.user.dto.ResendEmailVerificationRequest;
import com.doFast.dofastapp.user.dto.VerifyEmailRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users/email-verification")
public class EmailVerificationController {
    private final EmailVerificationService service;

    public EmailVerificationController(EmailVerificationService service) {
        this.service = service;
    }

    @PostMapping("/resend")
    public ResponseEntity<Void> resend(@Valid @RequestBody ResendEmailVerificationRequest request) {
        service.resend(request.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/verify")
    public ResponseEntity<Void> verify(@Valid @RequestBody VerifyEmailRequest request) {
        service.verify(request.token());
        return ResponseEntity.noContent().build();
    }
}
