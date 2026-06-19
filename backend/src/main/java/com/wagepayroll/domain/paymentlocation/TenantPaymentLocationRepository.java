package com.wagepayroll.domain.paymentlocation;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantPaymentLocationRepository extends JpaRepository<TenantPaymentLocationEntity, UUID> {

	Page<TenantPaymentLocationEntity> findByTenantIdAndCompanyId(UUID tenantId, UUID companyId, Pageable pageable);

	Page<TenantPaymentLocationEntity> findByTenantIdAndCompanyIdAndActive(UUID tenantId, UUID companyId,
			boolean active, Pageable pageable);

	Optional<TenantPaymentLocationEntity> findByIdAndTenantId(UUID id, UUID tenantId);

	boolean existsByTenantIdAndCompanyIdAndNameIgnoreCase(UUID tenantId, UUID companyId, String name);

	boolean existsByTenantIdAndCompanyIdAndNameIgnoreCaseAndIdNot(UUID tenantId, UUID companyId, String name,
			UUID excludeId);
}
