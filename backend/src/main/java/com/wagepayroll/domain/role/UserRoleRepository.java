package com.wagepayroll.domain.role;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRoleRepository extends JpaRepository<UserRoleEntity, UUID> {

	@Query("select ur.roleId from UserRoleEntity ur where ur.userId = :userId and ur.tenantId = :tenantId")
	List<UUID> findRoleIdsByUserAndTenant(@Param("userId") UUID userId, @Param("tenantId") UUID tenantId);
}
