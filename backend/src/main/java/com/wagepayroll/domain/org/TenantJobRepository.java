package com.wagepayroll.domain.org;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantJobRepository extends JpaRepository<TenantJobEntity, UUID> {

	Page<TenantJobEntity> findByTenantIdAndCompanyId(UUID tenantId, UUID companyId, Pageable pageable);

	Page<TenantJobEntity> findByTenantIdAndCompanyIdAndDepartmentId(UUID tenantId, UUID companyId, UUID departmentId,
			Pageable pageable);

	Page<TenantJobEntity> findByTenantIdAndCompanyIdAndActive(UUID tenantId, UUID companyId, boolean active,
			Pageable pageable);

	Optional<TenantJobEntity> findByIdAndTenantId(UUID id, UUID tenantId);

	Optional<TenantJobEntity> findByIdAndTenantIdAndCompanyId(UUID id, UUID tenantId, UUID companyId);

	boolean existsByTenantIdAndCompanyIdAndCode(UUID tenantId, UUID companyId, String code);

	boolean existsByTenantIdAndCompanyIdAndCodeAndIdNot(UUID tenantId, UUID companyId, String code, UUID id);
}
