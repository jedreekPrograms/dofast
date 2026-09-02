package com.doFast.dofastapp.user.controller;

import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.user.auth.session.AuthSessionCookieService;
import com.doFast.dofastapp.user.auth.session.AuthSessionService;
import com.doFast.dofastapp.user.dto.ChangePasswordRequest;
import com.doFast.dofastapp.user.dto.UpdateProfileRequest;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.service.UserService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class UserControllerTest {

    private final UserService userService = mock(UserService.class);
    private final AuthSessionService sessionService = mock(AuthSessionService.class);
    private final AuthSessionCookieService cookieService = mock(AuthSessionCookieService.class);
    private final UserController controller = new UserController(userService, sessionService, cookieService);

    @Test
    void accountReadsFailClosedBeforeServiceAccessWithoutPersistentIdentity() {
        assertThrows(ForbiddenOperationException.class, () -> controller.me(null));
        assertThrows(ForbiddenOperationException.class, () -> controller.me(new User()));

        verifyNoInteractions(userService, sessionService, cookieService);
    }

    @Test
    void profileUpdatesFailClosedBeforeServiceAccessWithoutPersistentIdentity() {
        UpdateProfileRequest request = new UpdateProfileRequest("updated-user", "bio", "Wroclaw");

        assertThrows(ForbiddenOperationException.class, () -> controller.updateProfile(null, request));
        assertThrows(ForbiddenOperationException.class, () -> controller.updateProfile(new User(), request));

        verifyNoInteractions(userService, sessionService, cookieService);
    }

    @Test
    void passwordChangesFailClosedBeforeServiceAccessWithoutPersistentIdentity() {
        ChangePasswordRequest request = new ChangePasswordRequest("OldPass123!", "NewPass456!");

        assertThrows(ForbiddenOperationException.class, () -> controller.changePassword(null, request));
        assertThrows(ForbiddenOperationException.class, () -> controller.changePassword(new User(), request));

        verifyNoInteractions(userService, sessionService, cookieService);
    }
}
