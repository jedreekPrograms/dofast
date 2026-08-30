package com.doFast.dofastapp.user.controller;

import com.doFast.dofastapp.user.auth.session.AuthSessionCookieService;
import com.doFast.dofastapp.user.auth.session.AuthSessionCredentials;
import com.doFast.dofastapp.user.auth.session.AuthSessionGrant;
import com.doFast.dofastapp.user.auth.session.AuthSessionService;
import com.doFast.dofastapp.user.dto.AuthResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users/session")
public class AuthSessionController {

    private final AuthSessionService sessionService;
    private final AuthSessionCookieService cookieService;

    public AuthSessionController(
            AuthSessionService sessionService,
            AuthSessionCookieService cookieService
    ) {
        this.sessionService = sessionService;
        this.cookieService = cookieService;
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        AuthSessionCredentials credentials = cookieService.requireCredentials(request);
        AuthSessionGrant grant = sessionService.refresh(
                credentials.refreshToken(),
                credentials.csrfToken()
        );
        cookieService.write(response, grant);
        return grant.response();
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        try {
            AuthSessionCredentials credentials = cookieService.optionalCredentials(request);
            if (credentials != null) {
                sessionService.logout(credentials.refreshToken(), credentials.csrfToken());
            }
        } finally {
            cookieService.clear(response);
        }
    }
}
