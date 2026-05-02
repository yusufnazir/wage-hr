package com.wagepayroll.domain.org;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TenantEmployeeRepository
		extends JpaRepository<TenantEmployeeEntity, UUID>, JpaSpecificationExecutor<TenantEmployeeEntity> {

	Optional<TenantEmployeeEntity> findByIdAndTenantId(UUID id, UUID tenantId);

	Optional<TenantEmployeeEntity> findByIdAndTenantIdAndCompanyId(UUID id, UUID tenantId, UUID companyId);
}
