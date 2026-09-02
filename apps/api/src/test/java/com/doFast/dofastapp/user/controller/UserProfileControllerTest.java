package com.doFast.dofastapp.user.controller;

import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.user.dto.UpdateUserServiceCategoriesRequest;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.service.UserProfileService;
import com.doFast.dofastapp.user.service.UserServiceCategoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class UserProfileControllerTest {

    @Mock private UserProfileService userProfileService;
    @Mock private UserServiceCategoryService userServiceCategoryService;

    @Test
    void rejectsMissingPrincipalBeforeCallingPrivateServiceCategoryOperations() {
        UserProfileController controller = new UserProfileController(userProfileService, userServiceCategoryService);
        UpdateUserServiceCategoriesRequest request = new UpdateUserServiceCategoriesRequest(List.of(2L));

        assertThrows(ForbiddenOperationException.class, () -> controller.getMyServiceCategories(null));
        assertThrows(ForbiddenOperationException.class, () -> controller.updateMyServiceCategories(null, request));

        verifyNoInteractions(userServiceCategoryService);
    }

    @Test
    void rejectsTransientPrincipalBeforeCallingPrivateServiceCategoryOperations() {
        UserProfileController controller = new UserProfileController(userProfileService, userServiceCategoryService);
        User transientUser = new User("user@example.com", "Użytkownik");
        UpdateUserServiceCategoriesRequest request = new UpdateUserServiceCategoriesRequest(List.of(2L));

        assertThrows(ForbiddenOperationException.class, () -> controller.getMyServiceCategories(transientUser));
        assertThrows(ForbiddenOperationException.class, () -> controller.updateMyServiceCategories(transientUser, request));

        verifyNoInteractions(userServiceCategoryService);
    }
}
