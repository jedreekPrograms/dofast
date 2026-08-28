package com.doFast.dofastapp.user.controller;

import com.doFast.dofastapp.user.dto.UserBlockResponse;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.service.UserBlockService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/user-blocks")
public class UserBlockController {

    private final UserBlockService userBlockService;

    public UserBlockController(UserBlockService userBlockService) {
        this.userBlockService = userBlockService;
    }

    @PutMapping("/{userId}")
    public UserBlockResponse block(
            @PathVariable Long userId,
            @AuthenticationPrincipal User user
    ) {
        return userBlockService.block(userId, user);
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unblock(
            @PathVariable Long userId,
            @AuthenticationPrincipal User user
    ) {
        userBlockService.unblock(userId, user);
    }

    @GetMapping
    public List<UserBlockResponse> mine(@AuthenticationPrincipal User user) {
        return userBlockService.mine(user);
    }
}
