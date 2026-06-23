package com.wagepayroll.domain.payrollstanding;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TenantEmployeePayrollStandingInstructionRepository
		extends JpaRepository<TenantEmployeePayrollStandingInstructionEntity, UUID> {

	Optional<TenantEmployeePayrollStandingInstructionEntity> findByIdAndTenantId(UUID id, UUID tenantId);

	List<TenantEmployeePayrollStandingInstructionEntity> findByTenantIdAndCompanyIdAndEmployeeIdOrderByEffectiveFromAsc(
			UUID tenantId, UUID companyId, UUID employeeId);

	@Query("""
			SELECT si FROM TenantEmployeePayrollStandingInstructionEntity si
			JOIN TenantWageComponentEntity wc ON wc.id = si.tenantWageComponentId AND wc.tenantId = si.tenantId
			WHERE si.tenantId = :tenantId AND si.companyId = :companyId AND si.employeeId = :employeeId
			ORDER BY wc.processingOrder ASC, wc.code ASC, si.effectiveFrom ASC
			""")
	List<TenantEmployeePayrollStandingInstructionEntity> findByTenantIdAndCompanyIdAndEmployeeIdOrderByWageComponentProcessingOrderAsc(
			@Param("tenantId") UUID tenantId, @Param("companyId") UUID companyId, @Param("employeeId") UUID employeeId);

	List<TenantEmployeePayrollStandingInstructionEntity> findByTenantIdAndCompanyIdAndEmployeeIdAndTenantWageComponentId(
			UUID tenantId, UUID companyId, UUID employeeId, UUID tenantWageComponentId);

	List<TenantEmployeePayrollStandingInstructionEntity> findByTenantIdAndCompanyId(UUID tenantId, UUID companyId);

	List<TenantEmployeePayrollStandingInstructionEntity> findByTenantIdAndCompanyIdAndEmployeeIdIn(UUID tenantId,
			UUID companyId, Collection<UUID> employeeIds);

	void deleteByTenantIdAndEmployeeId(UUID tenantId, UUID employeeId);
}
