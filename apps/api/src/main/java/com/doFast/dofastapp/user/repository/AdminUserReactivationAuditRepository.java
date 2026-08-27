package com.doFast.dofastapp.user.repository;

import com.doFast.dofastapp.user.entity.AdminUserReactivationAudit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminUserReactivationAuditRepository extends JpaRepository<AdminUserReactivationAudit, Long> {
}
