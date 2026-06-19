package com.wagepayroll.domain.wagecomponent;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TenantWageComponentDependencyRepository extends JpaRepository<TenantWageComponentDependencyEntity, UUID> {

	@Query("""
			SELECT d FROM TenantWageComponentDependencyEntity d
			WHERE d.tenantId = :tenantId
			  AND (d.tenantWageComponentId IN :componentIds
			    OR d.dependsOnTenantWageComponentId IN :componentIds)
			""")
	List<TenantWageComponentDependencyEntity> findByTenantIdTouchingComponents(@Param("tenantId") UUID tenantId,
			@Param("componentIds") Collection<UUID> componentIds);

	boolean existsByTenantIdAndTenantWageComponentIdAndDependsOnTenantWageComponentId(UUID tenantId,
			UUID tenantWageComponentId, UUID dependsOnTenantWageComponentId);
}
