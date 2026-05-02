package com.wagepayroll.domain.org;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantCompanyRepository extends JpaRepository<TenantCompanyEntity, UUID> {

	Page<TenantCompanyEntity> findByTenantId(UUID tenantId, Pageable pageable);

	Page<TenantCompanyEntity> findByTenantIdAndActive(UUID tenantId, boolean active, Pageable pageable);

	Optional<TenantCompanyEntity> findByIdAndTenantId(UUID id, UUID tenantId);

	boolean existsByTenantIdAndTaxId(UUID tenantId, String taxId);

	boolean existsByTenantIdAndTaxIdAndIdNot(UUID tenantId, String taxId, UUID id);
}
