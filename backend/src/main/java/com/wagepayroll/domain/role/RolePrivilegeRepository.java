package com.wagepayroll.domain.role;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RolePrivilegeRepository extends JpaRepository<RolePrivilegeEntity, UUID> {

	boolean existsByTenantIdAndRoleIdAndPrivilegeId(UUID tenantId, UUID roleId, UUID privilegeId);

	@Query("select rp.privilegeId from RolePrivilegeEntity rp where rp.tenantId = :tenantId and rp.roleId = :roleId")
	List<UUID> findPrivilegeIdsByTenantIdAndRoleId(@Param("tenantId") UUID tenantId, @Param("roleId") UUID roleId);

	@Modifying
	@Query("delete from RolePrivilegeEntity rp where rp.tenantId = :tenantId and rp.roleId = :roleId")
	void deleteByTenantIdAndRoleId(@Param("tenantId") UUID tenantId, @Param("roleId") UUID roleId);
}
