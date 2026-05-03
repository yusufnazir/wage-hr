package com.wagepayroll.domain.org;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantWorkTimeRepository extends JpaRepository<TenantWorkTimeEntity, UUID> {

	Page<TenantWorkTimeEntity> findByTenantId(UUID tenantId, Pageable pageable);

	Page<TenantWorkTimeEntity> findByTenantIdAndActive(UUID tenantId, boolean active, Pageable pageable);

	Page<TenantWorkTimeEntity> findByTenantIdAndCompanyId(UUID tenantId, UUID companyId, Pageable pageable);

	Page<TenantWorkTimeEntity> findByTenantIdAndCompanyIdAndActive(UUID tenantId, UUID companyId, boolean active,
			Pageable pageable);

	Optional<TenantWorkTimeEntity> findByIdAndTenantId(UUID id, UUID tenantId);

	Optional<TenantWorkTimeEntity> findByIdAndTenantIdAndCompanyId(UUID id, UUID tenantId, UUID companyId);

	boolean existsByTenantIdAndCompanyIdAndCode(UUID tenantId, UUID companyId, String code);

	boolean existsByTenantIdAndCompanyIdAndCodeAndIdNot(UUID tenantId, UUID companyId, String code, UUID id);
}
