package com.doFast.dofastapp.user.controller;

import com.doFast.dofastapp.user.auth.apple.AppleIdentity;
import com.doFast.dofastapp.user.auth.apple.AppleLoginChallengeResponse;
import com.doFast.dofastapp.user.auth.apple.AppleLoginChallengeService;
import com.doFast.dofastapp.user.auth.apple.AppleLoginRequest;
import com.doFast.dofastapp.user.auth.apple.AppleSignInService;
import com.doFast.dofastapp.user.dto.AuthResponse;
import com.doFast.dofastapp.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users/login/apple")
public class AppleAuthController {

    private final AppleLoginChallengeService challengeService;
    private final AppleSignInService appleSignInService;
    private final UserService userService;

    public AppleAuthController(
            AppleLoginChallengeService challengeService,
            AppleSignInService appleSignInService,
            UserService userService
    ) {
        this.challengeService = challengeService;
        this.appleSignInService = appleSignInService;
        this.userService = userService;
    }

    @PostMapping("/challenge")
    public AppleLoginChallengeResponse createChallenge() {
        return challengeService.createChallenge();
    }

    @PostMapping
    public AuthResponse login(@RequestBody @Valid AppleLoginRequest request) {
        AppleIdentity identity = appleSignInService.authenticate(request);
        return userService.loginWithAppleIdentity(identity);
    }
}