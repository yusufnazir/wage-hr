package com.wagepayroll.domain.wagecomponent;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantWageComponentBalanceTransactionRepository
		extends JpaRepository<TenantWageComponentBalanceTransactionEntity, UUID> {

	boolean existsByTenantIdAndBalanceIdAndPayPeriodRunId(UUID tenantId, UUID balanceId, UUID payPeriodRunId);

	long countByTenantIdAndPayPeriodRunId(UUID tenantId, UUID payPeriodRunId);
}
