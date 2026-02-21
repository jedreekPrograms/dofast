package com.doFast.dofastapp.user.controller;

import com.doFast.dofastapp.user.dto.UserProfileResponse;
import com.doFast.dofastapp.user.service.UserProfileService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserProfileController {

    private final UserProfileService userProfileService;

    public UserProfileController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @GetMapping("/{id}/profile")
    public UserProfileResponse getProfile(@PathVariable Long id) {
        return userProfileService.getProfile(id);
    }
}
