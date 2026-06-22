package com.wagepayroll.domain.org;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantPayPeriodRunRepository extends JpaRepository<TenantPayPeriodRunEntity, UUID> {

	Page<TenantPayPeriodRunEntity> findByTenantIdAndPayPeriodId(UUID tenantId, UUID payPeriodId, Pageable pageable);

	List<TenantPayPeriodRunEntity> findByTenantIdAndPayPeriodIdAndRunType(UUID tenantId, UUID payPeriodId,
			String runType);

	Optional<TenantPayPeriodRunEntity> findByIdAndTenantId(UUID id, UUID tenantId);

	long countByTenantIdAndPayPeriodId(UUID tenantId, UUID payPeriodId);
}
