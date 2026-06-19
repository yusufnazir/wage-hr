package com.wagepayroll.domain.wagecomponent;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantWageComponentTransactionRepository
		extends JpaRepository<TenantWageComponentTransactionEntity, UUID> {

	Optional<TenantWageComponentTransactionEntity> findByTenantIdAndPayPeriodIdAndEmployeeIdAndTenantWageComponentId(
			UUID tenantId, UUID payPeriodId, UUID employeeId, UUID tenantWageComponentId);

	Optional<TenantWageComponentTransactionEntity> findByIdAndTenantId(UUID id, UUID tenantId);

	Page<TenantWageComponentTransactionEntity> findByTenantIdAndCompanyIdAndPayPeriodId(UUID tenantId, UUID companyId,
			UUID payPeriodId, Pageable pageable);

	Page<TenantWageComponentTransactionEntity> findByTenantIdAndCompanyIdAndPayPeriodIdAndEmployeeId(UUID tenantId,
			UUID companyId, UUID payPeriodId, UUID employeeId, Pageable pageable);

	List<TenantWageComponentTransactionEntity> findByTenantIdAndCompanyIdAndPayPeriodId(UUID tenantId, UUID companyId,
			UUID payPeriodId);

	List<TenantWageComponentTransactionEntity> findByTenantIdAndCompanyIdAndPayPeriodIdAndEmployeeIdIn(UUID tenantId,
			UUID companyId, UUID payPeriodId, Collection<UUID> employeeIds);
}
