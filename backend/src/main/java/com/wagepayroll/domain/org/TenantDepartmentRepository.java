package com.wagepayroll.domain.org;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantDepartmentRepository extends JpaRepository<TenantDepartmentEntity, UUID> {

	Page<TenantDepartmentEntity> findByTenantId(UUID tenantId, Pageable pageable);

	Page<TenantDepartmentEntity> findByTenantIdAndActive(UUID tenantId, boolean active, Pageable pageable);

	Page<TenantDepartmentEntity> findByTenantIdAndCompanyId(UUID tenantId, UUID companyId, Pageable pageable);

	Page<TenantDepartmentEntity> findByTenantIdAndCompanyIdAndActive(UUID tenantId, UUID companyId, boolean active,
			Pageable pageable);

	Optional<TenantDepartmentEntity> findByIdAndTenantId(UUID id, UUID tenantId);

	Optional<TenantDepartmentEntity> findByIdAndTenantIdAndCompanyId(UUID id, UUID tenantId, UUID companyId);

	boolean existsByTenantIdAndCompanyIdAndCode(UUID tenantId, UUID companyId, String code);

	boolean existsByTenantIdAndCompanyIdAndCodeAndIdNot(UUID tenantId, UUID companyId, String code, UUID id);

	List<TenantDepartmentEntity> findByTenantIdAndIdIn(UUID tenantId, Collection<UUID> ids);
}
