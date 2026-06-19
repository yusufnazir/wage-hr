package com.wagepayroll.domain.banktemplate;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantBankTemplateRepository extends JpaRepository<TenantBankTemplateEntity, UUID> {

	Page<TenantBankTemplateEntity> findByTenantIdAndCompanyId(UUID tenantId, UUID companyId, Pageable pageable);

	Page<TenantBankTemplateEntity> findByTenantIdAndCompanyIdAndActive(UUID tenantId, UUID companyId, boolean active,
			Pageable pageable);

	Optional<TenantBankTemplateEntity> findByIdAndTenantId(UUID id, UUID tenantId);

	boolean existsByTenantIdAndCompanyIdAndPlatformBankTemplateId(UUID tenantId, UUID companyId,
			UUID platformBankTemplateId);
}
