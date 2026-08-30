package com.doFast.dofastapp.user.controller;

import com.doFast.dofastapp.user.auth.apple.AppleIdentity;
import com.doFast.dofastapp.user.auth.apple.AppleLoginChallengeResponse;
import com.doFast.dofastapp.user.auth.apple.AppleLoginChallengeService;
import com.doFast.dofastapp.user.auth.apple.AppleLoginRequest;
import com.doFast.dofastapp.user.auth.apple.AppleSignInService;
import com.doFast.dofastapp.user.auth.session.AuthSessionCookieService;
import com.doFast.dofastapp.user.auth.session.AuthSessionGrant;
import com.doFast.dofastapp.user.auth.session.AuthSessionService;
import com.doFast.dofastapp.user.dto.AuthResponse;
import com.doFast.dofastapp.user.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
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
    private final AuthSessionService sessionService;
    private final AuthSessionCookieService cookieService;

    public AppleAuthController(
            AppleLoginChallengeService challengeService,
            AppleSignInService appleSignInService,
            UserService userService,
            AuthSessionService sessionService,
            AuthSessionCookieService cookieService
    ) {
        this.challengeService = challengeService;
        this.appleSignInService = appleSignInService;
        this.userService = userService;
        this.sessionService = sessionService;
        this.cookieService = cookieService;
    }

    @PostMapping("/challenge")
    public AppleLoginChallengeResponse createChallenge() {
        return challengeService.createChallenge();
    }

    @PostMapping
    public AuthResponse login(
            @RequestBody @Valid AppleLoginRequest request,
            HttpServletResponse response
    ) {
        AppleIdentity identity = appleSignInService.authenticate(request);
        AuthSessionGrant grant = sessionService.issue(userService.loginWithAppleIdentity(identity));
        cookieService.write(response, grant);
        return grant.response();
    }
}
