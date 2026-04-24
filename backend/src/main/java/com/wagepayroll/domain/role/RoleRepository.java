package com.wagepayroll.domain.role;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<RoleEntity, UUID> {

	List<RoleEntity> findByTenantId(UUID tenantId);

	Optional<RoleEntity> findByTenantIdAndId(UUID tenantId, UUID id);
}
