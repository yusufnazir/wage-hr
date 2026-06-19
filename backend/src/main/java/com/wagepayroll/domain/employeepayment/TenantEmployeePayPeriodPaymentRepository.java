package com.wagepayroll.domain.employeepayment;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TenantEmployeePayPeriodPaymentRepository
		extends JpaRepository<TenantEmployeePayPeriodPaymentEntity, UUID> {

	List<TenantEmployeePayPeriodPaymentEntity> findByTenantIdAndEmployeeIdAndPayPeriodIdOrderByCreatedAtAsc(
			UUID tenantId, UUID employeeId, UUID payPeriodId);

	@Query("""
			select p from TenantEmployeePayPeriodPaymentEntity p
			where p.tenantId = :tenantId and p.employeeId = :employeeId
			order by p.createdAt desc
			""")
	List<TenantEmployeePayPeriodPaymentEntity> findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(
			@Param("tenantId") UUID tenantId, @Param("employeeId") UUID employeeId);

	boolean existsByTenantIdAndPayPeriodRunIdAndEmployeeId(UUID tenantId, UUID payPeriodRunId, UUID employeeId);

	@Query(value = """
			select p from TenantEmployeePayPeriodPaymentEntity p, TenantPayPeriodEntity pp
			where p.payPeriodId = pp.id
			  and p.tenantId = :tenantId and p.employeeId = :employeeId
			  and pp.status = 'CLOSED'
			  and (:year is null or pp.year = :year)
			  and (:payPeriodId is null or pp.id = :payPeriodId)
			order by pp.endDate desc, p.createdAt asc
			""",
			countQuery = """
			select count(p) from TenantEmployeePayPeriodPaymentEntity p, TenantPayPeriodEntity pp
			where p.payPeriodId = pp.id
			  and p.tenantId = :tenantId and p.employeeId = :employeeId
			  and pp.status = 'CLOSED'
			  and (:year is null or pp.year = :year)
			  and (:payPeriodId is null or pp.id = :payPeriodId)
			""")
	Page<TenantEmployeePayPeriodPaymentEntity> findClosedHistory(@Param("tenantId") UUID tenantId,
			@Param("employeeId") UUID employeeId, @Param("year") Integer year, @Param("payPeriodId") UUID payPeriodId,
			Pageable pageable);
}
