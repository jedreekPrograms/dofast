package com.doFast.dofastapp.user.repository;

import com.doFast.dofastapp.user.entity.UserAuthIdentity;
import com.doFast.dofastapp.user.enums.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserAuthIdentityRepository extends JpaRepository<UserAuthIdentity, Long> {
    Optional<UserAuthIdentity> findByProviderAndProviderSubject(AuthProvider provider, String providerSubject);
    boolean existsByUser_IdAndProvider(Long userId, AuthProvider provider);
}