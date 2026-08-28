package com.doFast.dofastapp.user.controller;

import com.doFast.dofastapp.user.dto.UpdateUserServiceAreaRequest;
import com.doFast.dofastapp.user.dto.UserServiceAreaResponse;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.service.UserServiceAreaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users/me/service-area")
public class UserServiceAreaController {

    private final UserServiceAreaService userServiceAreaService;

    public UserServiceAreaController(UserServiceAreaService userServiceAreaService) {
        this.userServiceAreaService = userServiceAreaService;
    }

    @GetMapping
    public UserServiceAreaResponse get(@AuthenticationPrincipal User user) {
        return userServiceAreaService.getForUser(user);
    }

    @PutMapping
    public UserServiceAreaResponse update(
            @RequestBody @Valid UpdateUserServiceAreaRequest request,
            @AuthenticationPrincipal User user
    ) {
        return userServiceAreaService.update(user, request);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@AuthenticationPrincipal User user) {
        userServiceAreaService.clear(user);
    }
}
