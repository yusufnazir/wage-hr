package com.wagepayroll.domain.payroll;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantPayrollYtdAccumulatorRepository extends JpaRepository<TenantPayrollYtdAccumulatorEntity, UUID> {

	Optional<TenantPayrollYtdAccumulatorEntity> findByTenantIdAndEmployeeIdAndTaxYearAndAccumulatorCode(UUID tenantId,
			UUID employeeId, int taxYear, String accumulatorCode);

	List<TenantPayrollYtdAccumulatorEntity> findByTenantIdAndEmployeeIdAndTaxYearOrderByAccumulatorCodeAsc(
			UUID tenantId, UUID employeeId, int taxYear);
}
