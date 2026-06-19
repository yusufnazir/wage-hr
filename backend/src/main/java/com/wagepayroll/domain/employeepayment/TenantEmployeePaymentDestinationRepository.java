package com.wagepayroll.domain.employeepayment;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantEmployeePaymentDestinationRepository
		extends JpaRepository<TenantEmployeePaymentDestinationEntity, UUID> {

	List<TenantEmployeePaymentDestinationEntity> findByTenantIdAndEmployeeIdOrderBySortOrderAsc(UUID tenantId,
			UUID employeeId);

	List<TenantEmployeePaymentDestinationEntity> findByTenantIdAndEmployeeIdAndActiveTrueOrderBySortOrderAsc(
			UUID tenantId, UUID employeeId);

	void deleteByTenantIdAndEmployeeId(UUID tenantId, UUID employeeId);
}
