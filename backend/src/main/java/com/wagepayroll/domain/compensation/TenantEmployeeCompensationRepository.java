package com.wagepayroll.domain.compensation;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantEmployeeCompensationRepository
		extends JpaRepository<TenantEmployeeCompensationEntity, UUID> {

	Optional<TenantEmployeeCompensationEntity> findByEmployeeIdAndTenantId(UUID employeeId, UUID tenantId);

	List<TenantEmployeeCompensationEntity> findByTenantIdAndEmployeeIdIn(UUID tenantId, Collection<UUID> employeeIds);
}
