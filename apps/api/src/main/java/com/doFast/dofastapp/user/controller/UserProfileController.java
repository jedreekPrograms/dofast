package com.doFast.dofastapp.user.controller;

import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.user.dto.UpdateUserServiceCategoriesRequest;
import com.doFast.dofastapp.user.dto.UserProfileResponse;
import com.doFast.dofastapp.user.dto.UserServiceCategoryResponse;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.service.UserProfileService;
import com.doFast.dofastapp.user.service.UserServiceCategoryService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/users")
public class UserProfileController {

    private final UserProfileService userProfileService;
    private final UserServiceCategoryService userServiceCategoryService;

    public UserProfileController(
            UserProfileService userProfileService,
            UserServiceCategoryService userServiceCategoryService
    ) {
        this.userProfileService = userProfileService;
        this.userServiceCategoryService = userServiceCategoryService;
    }

    @GetMapping("/{id}/profile")
    public UserProfileResponse getProfile(@PathVariable Long id) {
        return userProfileService.getProfile(id);
    }

    @GetMapping("/me/service-categories")
    public List<UserServiceCategoryResponse> getMyServiceCategories(@AuthenticationPrincipal User user) {
        return userServiceCategoryService.getForUser(requireUserId(user));
    }

    @PutMapping("/me/service-categories")
    public List<UserServiceCategoryResponse> updateMyServiceCategories(
            @AuthenticationPrincipal User user,
            @RequestBody @Valid UpdateUserServiceCategoriesRequest request
    ) {
        requireUserId(user);
        return userServiceCategoryService.replaceForUser(user, request);
    }

    private Long requireUserId(User user) {
        if (user == null || user.getId() == null) {
            throw new ForbiddenOperationException("Zaloguj się, aby zarządzać specjalizacjami");
        }
        return user.getId();
    }
}
