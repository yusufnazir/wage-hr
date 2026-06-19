package com.wagepayroll.domain.payrollbase;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantWageComponentBaseEffectRepository extends JpaRepository<TenantWageComponentBaseEffectEntity, UUID> {

	List<TenantWageComponentBaseEffectEntity> findByTenantIdAndTenantWageComponentIdAndActiveIsTrue(UUID tenantId,
			UUID tenantWageComponentId);

	List<TenantWageComponentBaseEffectEntity> findByTenantIdAndTenantWageComponentIdInAndActiveIsTrue(UUID tenantId,
			Collection<UUID> tenantWageComponentIds);

	boolean existsByTenantIdAndTenantWageComponentIdAndPlatformPayrollBaseId(UUID tenantId, UUID tenantWageComponentId,
			UUID payrollBaseId);
}
