package com.wagepayroll.domain.wagecomponent;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantPayrollLedgerPostingRepository extends JpaRepository<TenantPayrollLedgerPostingEntity, UUID> {

	boolean existsByTenantIdAndPayPeriodRunId(UUID tenantId, UUID payPeriodRunId);

	long countByTenantIdAndPayPeriodRunId(UUID tenantId, UUID payPeriodRunId);
}
