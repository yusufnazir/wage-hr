package com.wagepayroll.domain.componentgroup;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantComponentGroupRepository extends JpaRepository<TenantComponentGroupEntity, UUID> {

	Page<TenantComponentGroupEntity> findByTenantIdAndCompanyIdOrderBySortOrderAscIdAsc(UUID tenantId, UUID companyId,
			Pageable pageable);

	Page<TenantComponentGroupEntity> findByTenantIdAndCompanyIdAndActiveOrderBySortOrderAscIdAsc(UUID tenantId, UUID companyId,
			boolean active, Pageable pageable);

	Optional<TenantComponentGroupEntity> findByIdAndTenantIdAndCompanyId(UUID id, UUID tenantId, UUID companyId);

	boolean existsByTenantIdAndCompanyIdAndPlatformGroupTemplate_Id(UUID tenantId, UUID companyId,
			UUID platformGroupTemplateId);
}
