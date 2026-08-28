package com.doFast.dofastapp.user.repository;

import com.doFast.dofastapp.user.entity.UserServiceArea;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserServiceAreaRepository extends JpaRepository<UserServiceArea, Long> {
    Optional<UserServiceArea> findByUser_Id(Long userId);
    void deleteByUser_Id(Long userId);
}
