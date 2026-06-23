package com.wagepayroll.domain.wagecomponent;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantPayrollResultLineRepository extends JpaRepository<TenantPayrollResultLineEntity, UUID> {

	boolean existsByTenantIdAndPayPeriodRunId(UUID tenantId, UUID payPeriodRunId);

	long countByTenantIdAndPayPeriodRunId(UUID tenantId, UUID payPeriodRunId);

	List<TenantPayrollResultLineEntity> findByTenantIdAndPayPeriodRunIdOrderByEmployeeIdAscProcessingOrderSnapshotAsc(
			UUID tenantId, UUID payPeriodRunId);

	List<TenantPayrollResultLineEntity> findByTenantIdAndPayPeriodRunIdAndEmployeeIdOrderByProcessingOrderSnapshotAsc(
			UUID tenantId, UUID payPeriodRunId, UUID employeeId);

	boolean existsByTenantIdAndEmployeeId(UUID tenantId, UUID employeeId);
}
