package com.wagepayroll.domain.org;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantPayPeriodRunRepository extends JpaRepository<TenantPayPeriodRunEntity, UUID> {

	Page<TenantPayPeriodRunEntity> findByTenantIdAndPayPeriodId(UUID tenantId, UUID payPeriodId, Pageable pageable);

	Optional<TenantPayPeriodRunEntity> findByIdAndTenantId(UUID id, UUID tenantId);

	long countByTenantIdAndPayPeriodId(UUID tenantId, UUID payPeriodId);
}
