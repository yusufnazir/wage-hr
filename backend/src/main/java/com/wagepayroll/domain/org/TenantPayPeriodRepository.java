package com.wagepayroll.domain.org;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantPayPeriodRepository extends JpaRepository<TenantPayPeriodEntity, UUID> {

	Page<TenantPayPeriodEntity> findByTenantId(UUID tenantId, Pageable pageable);

	Page<TenantPayPeriodEntity> findByTenantIdAndCompanyId(UUID tenantId, UUID companyId, Pageable pageable);

	Page<TenantPayPeriodEntity> findByTenantIdAndCompanyIdAndYear(UUID tenantId, UUID companyId, int year,
			Pageable pageable);

	Page<TenantPayPeriodEntity> findByTenantIdAndCompanyIdAndStatus(UUID tenantId, UUID companyId, String status,
			Pageable pageable);

	Page<TenantPayPeriodEntity> findByTenantIdAndCompanyIdAndYearAndStatus(UUID tenantId, UUID companyId, int year,
			String status, Pageable pageable);

	Page<TenantPayPeriodEntity> findByTenantIdAndYear(UUID tenantId, int year, Pageable pageable);

	Page<TenantPayPeriodEntity> findByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable);

	Page<TenantPayPeriodEntity> findByTenantIdAndYearAndStatus(UUID tenantId, int year, String status,
			Pageable pageable);

	Optional<TenantPayPeriodEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
