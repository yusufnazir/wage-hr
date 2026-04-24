package com.wagepayroll.domain.role;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RolePrivilegeRepository extends JpaRepository<RolePrivilegeEntity, UUID> {

	boolean existsByTenantIdAndRoleIdAndPrivilegeId(UUID tenantId, UUID roleId, UUID privilegeId);
}
