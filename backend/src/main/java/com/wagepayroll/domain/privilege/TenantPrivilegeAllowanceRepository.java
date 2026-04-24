package com.wagepayroll.domain.privilege;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantPrivilegeAllowanceRepository extends JpaRepository<TenantPrivilegeAllowanceEntity, UUID> {

	boolean existsByTenantIdAndPrivilegeId(UUID tenantId, UUID privilegeId);

	List<TenantPrivilegeAllowanceEntity> findByTenantId(UUID tenantId);

	void deleteByTenantId(UUID tenantId);
}
