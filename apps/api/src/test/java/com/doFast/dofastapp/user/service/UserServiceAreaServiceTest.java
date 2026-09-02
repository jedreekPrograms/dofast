package com.doFast.dofastapp.user.service;

import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.user.dto.UpdateUserServiceAreaRequest;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.entity.UserServiceArea;
import com.doFast.dofastapp.user.repository.UserServiceAreaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceAreaServiceTest {

    @Mock private UserServiceAreaRepository userServiceAreaRepository;
    @Mock private User user;

    @Test
    void returnsExplicitNotConfiguredState() {
        when(user.getId()).thenReturn(7L);
        when(userServiceAreaRepository.findByUser_Id(7L)).thenReturn(Optional.empty());

        UserServiceAreaService service = new UserServiceAreaService(userServiceAreaRepository);
        var response = service.getForUser(user);

        assertFalse(response.configured());
    }

    @Test
    void upsertsPointWithLongitudeAsXAndLatitudeAsY() {
        when(user.getId()).thenReturn(9L);
        when(userServiceAreaRepository.findByUser_Id(9L)).thenReturn(Optional.empty());
        when(userServiceAreaRepository.save(any(UserServiceArea.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserServiceAreaService service = new UserServiceAreaService(userServiceAreaRepository);
        var response = service.update(
                user,
                new UpdateUserServiceAreaRequest(51.1079, 17.0385, 30)
        );

        ArgumentCaptor<UserServiceArea> captor = ArgumentCaptor.forClass(UserServiceArea.class);
        verify(userServiceAreaRepository).save(captor.capture());
        UserServiceArea saved = captor.getValue();

        assertEquals(17.0385, saved.getCenterLocation().getX(), 0.000001);
        assertEquals(51.1079, saved.getCenterLocation().getY(), 0.000001);
        assertEquals(30_000, saved.getRadiusMeters());
        assertEquals(51.1079, response.latitude(), 0.000001);
        assertEquals(17.0385, response.longitude(), 0.000001);
        assertEquals(30, response.radiusKm());
    }

    @Test
    void clearingAreaIsOwnerScopedAndIdempotentAtRepositoryBoundary() {
        when(user.getId()).thenReturn(11L);

        UserServiceAreaService service = new UserServiceAreaService(userServiceAreaRepository);
        service.clear(user);

        verify(userServiceAreaRepository).deleteByUser_Id(11L);
    }

    @Test
    void rejectsMissingIdentityBeforeAnyRepositoryAccess() {
        UserServiceAreaService service = new UserServiceAreaService(userServiceAreaRepository);
        UpdateUserServiceAreaRequest request = new UpdateUserServiceAreaRequest(51.1079, 17.0385, 30);

        assertThrows(ForbiddenOperationException.class, () -> service.getForUser(null));
        assertThrows(ForbiddenOperationException.class, () -> service.update(null, request));
        assertThrows(ForbiddenOperationException.class, () -> service.clear(null));

        verifyNoInteractions(userServiceAreaRepository);
    }

    @Test
    void rejectsTransientIdentityBeforeAnyRepositoryAccess() {
        when(user.getId()).thenReturn(null);
        UserServiceAreaService service = new UserServiceAreaService(userServiceAreaRepository);
        UpdateUserServiceAreaRequest request = new UpdateUserServiceAreaRequest(51.1079, 17.0385, 30);

        assertThrows(ForbiddenOperationException.class, () -> service.getForUser(user));
        assertThrows(ForbiddenOperationException.class, () -> service.update(user, request));
        assertThrows(ForbiddenOperationException.class, () -> service.clear(user));

        verifyNoInteractions(userServiceAreaRepository);
    }
}
