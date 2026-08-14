package com.wagepayroll.domain.org;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TenantEmployeeRepository
		extends JpaRepository<TenantEmployeeEntity, UUID>, JpaSpecificationExecutor<TenantEmployeeEntity> {

	Optional<TenantEmployeeEntity> findByIdAndTenantId(UUID id, UUID tenantId);

	Optional<TenantEmployeeEntity> findByIdAndTenantIdAndCompanyId(UUID id, UUID tenantId, UUID companyId);

	Optional<TenantEmployeeEntity> findByTenantIdAndUserId(UUID tenantId, UUID userId);

	boolean existsByTenantIdAndUserIdAndIdNot(UUID tenantId, UUID userId, UUID id);

	boolean existsByTenantIdAndCompanyIdAndBadgeNumber(UUID tenantId, UUID companyId, String badgeNumber);

	boolean existsByTenantIdAndCompanyIdAndBadgeNumberAndIdNot(UUID tenantId, UUID companyId, String badgeNumber,
			UUID id);

	@Query("""
			SELECT e.id FROM TenantEmployeeEntity e
			WHERE e.tenantId = :tenantId AND e.companyId = :companyId AND e.active = true
			  AND e.status <> 'DRAFT'
			ORDER BY e.badgeNumber ASC
			""")
	List<UUID> findActiveIdsByTenantIdAndCompanyId(@Param("tenantId") UUID tenantId,
			@Param("companyId") UUID companyId);

	List<TenantEmployeeEntity> findByTenantIdAndCompanyIdOrderByBadgeNumberAsc(UUID tenantId, UUID companyId);

	List<TenantEmployeeEntity> findByTenantIdAndCompanyIdAndIdIn(UUID tenantId, UUID companyId,
			Collection<UUID> employeeIds);
}
