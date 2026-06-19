package com.wagepayroll.domain.wagecomponent;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantWageComponentRepository extends JpaRepository<TenantWageComponentEntity, UUID> {

	List<TenantWageComponentEntity> findByTenantIdAndCompanyIdAndActiveIsTrueOrderByProcessingOrderAsc(UUID tenantId,
			UUID companyId);

	List<TenantWageComponentEntity> findByTenantIdAndCompanyIdAndActiveIsTrueAndApplyInPayrollIsTrueAndAuxiliaryIsFalseOrderByProcessingOrderAsc(
			UUID tenantId, UUID companyId);

	Optional<TenantWageComponentEntity> findByIdAndTenantIdAndCompanyId(UUID id, UUID tenantId, UUID companyId);

	Optional<TenantWageComponentEntity> findByIdAndTenantId(UUID id, UUID tenantId);

	Optional<TenantWageComponentEntity> findByTenantIdAndCompanyIdAndPlatformTemplateId(UUID tenantId, UUID companyId,
			UUID platformTemplateId);

	Page<TenantWageComponentEntity> findByTenantIdAndCompanyId(UUID tenantId, UUID companyId, Pageable pageable);

	Page<TenantWageComponentEntity> findByTenantIdAndCompanyIdAndActive(UUID tenantId, UUID companyId, boolean active,
			Pageable pageable);

	boolean existsByTenantIdAndCompanyIdAndCodeIgnoreCase(UUID tenantId, UUID companyId, String code);

	boolean existsByTenantIdAndCompanyIdAndCodeIgnoreCaseAndIdNot(UUID tenantId, UUID companyId, String code, UUID id);

	long countByPlatformTemplateId(UUID platformTemplateId);

	List<TenantWageComponentEntity> findByTenantIdAndCompanyIdAndIdIn(UUID tenantId, UUID companyId,
			Collection<UUID> componentIds);
}
