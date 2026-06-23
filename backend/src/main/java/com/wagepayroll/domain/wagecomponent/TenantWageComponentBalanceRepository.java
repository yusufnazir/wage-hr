package com.wagepayroll.domain.wagecomponent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantWageComponentBalanceRepository extends JpaRepository<TenantWageComponentBalanceEntity, UUID> {

	Optional<TenantWageComponentBalanceEntity> findByTenantIdAndCompanyIdAndEmployeeIdAndTenantWageComponentId(
			UUID tenantId, UUID companyId, UUID employeeId, UUID tenantWageComponentId);

	List<TenantWageComponentBalanceEntity> findByTenantIdAndEmployeeId(UUID tenantId, UUID employeeId);

	void deleteByTenantIdAndEmployeeId(UUID tenantId, UUID employeeId);
}
