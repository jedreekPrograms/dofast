package com.doFast.dofastapp.user.controller;

import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.user.auth.session.AuthSessionCookieService;
import com.doFast.dofastapp.user.auth.session.AuthSessionGrant;
import com.doFast.dofastapp.user.auth.session.AuthSessionService;
import com.doFast.dofastapp.user.dto.AuthResponse;
import com.doFast.dofastapp.user.dto.ChangePasswordRequest;
import com.doFast.dofastapp.user.dto.GoogleLoginRequest;
import com.doFast.dofastapp.user.dto.LoginRequest;
import com.doFast.dofastapp.user.dto.UpdateProfileRequest;
import com.doFast.dofastapp.user.dto.UserRequest;
import com.doFast.dofastapp.user.dto.UserResponse;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;
    private final AuthSessionService sessionService;
    private final AuthSessionCookieService cookieService;

    public UserController(
            UserService userService,
            AuthSessionService sessionService,
            AuthSessionCookieService cookieService
    ) {
        this.userService = userService;
        this.sessionService = sessionService;
        this.cookieService = cookieService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createUser(@RequestBody @Valid UserRequest request) {
        return userService.createUser(request);
    }

    @PostMapping("/login")
    public AuthResponse login(
            @RequestBody @Valid LoginRequest request,
            HttpServletResponse response
    ) {
        return issueSession(userService.login(request), response);
    }

    @PostMapping("/login/google")
    public AuthResponse loginWithGoogle(
            @RequestBody @Valid GoogleLoginRequest request,
            HttpServletResponse response
    ) {
        return issueSession(userService.loginWithGoogle(request), response);
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal User user) {
        requireUserId(user);
        return userService.getCurrentUser(user);
    }

    @PatchMapping("/me/profile")
    public UserResponse updateProfile(
            @AuthenticationPrincipal User user,
            @RequestBody @Valid UpdateProfileRequest request
    ) {
        requireUserId(user);
        return userService.updateProfile(user, request);
    }

    @PatchMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(
            @AuthenticationPrincipal User user,
            @RequestBody @Valid ChangePasswordRequest request
    ) {
        requireUserId(user);
        userService.changePassword(user, request);
    }

    private AuthResponse issueSession(AuthResponse authResponse, HttpServletResponse response) {
        AuthSessionGrant grant = sessionService.issue(authResponse);
        cookieService.write(response, grant);
        return grant.response();
    }

    private Long requireUserId(User user) {
        if (user == null || user.getId() == null) {
            throw new ForbiddenOperationException("Zaloguj się, aby zarządzać kontem");
        }
        return user.getId();
    }
}
