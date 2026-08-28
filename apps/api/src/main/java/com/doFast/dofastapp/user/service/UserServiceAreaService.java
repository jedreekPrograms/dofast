package com.doFast.dofastapp.user.service;

import com.doFast.dofastapp.common.exception.BusinessException;
import com.doFast.dofastapp.user.dto.UpdateUserServiceAreaRequest;
import com.doFast.dofastapp.user.dto.UserServiceAreaResponse;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.entity.UserServiceArea;
import com.doFast.dofastapp.user.repository.UserServiceAreaRepository;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserServiceAreaService {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    private final UserServiceAreaRepository userServiceAreaRepository;

    public UserServiceAreaService(UserServiceAreaRepository userServiceAreaRepository) {
        this.userServiceAreaRepository = userServiceAreaRepository;
    }

    public UserServiceAreaResponse getForUser(User user) {
        return userServiceAreaRepository.findByUser_Id(user.getId())
                .map(this::toResponse)
                .orElseGet(UserServiceAreaResponse::notConfigured);
    }

    @Transactional
    public UserServiceAreaResponse update(User user, UpdateUserServiceAreaRequest request) {
        validateFiniteCoordinates(request.latitude(), request.longitude());

        UserServiceArea area = userServiceAreaRepository.findByUser_Id(user.getId())
                .orElseGet(() -> new UserServiceArea(user));
        Point center = GEOMETRY_FACTORY.createPoint(new Coordinate(request.longitude(), request.latitude()));
        area.setCenterLocation(center);
        area.setRadiusMeters(request.radiusKm() * 1000);

        return toResponse(userServiceAreaRepository.save(area));
    }

    @Transactional
    public void clear(User user) {
        userServiceAreaRepository.deleteByUser_Id(user.getId());
    }

    private void validateFiniteCoordinates(Double latitude, Double longitude) {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)) {
            throw new BusinessException("Współrzędne obszaru działania muszą być skończonymi liczbami");
        }
    }

    private UserServiceAreaResponse toResponse(UserServiceArea area) {
        Point center = area.getCenterLocation();
        return new UserServiceAreaResponse(
                true,
                center.getY(),
                center.getX(),
                area.getRadiusMeters() / 1000,
                area.getUpdatedAt()
        );
    }
}
