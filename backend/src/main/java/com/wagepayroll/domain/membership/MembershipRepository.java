package com.wagepayroll.domain.membership;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipRepository extends JpaRepository<MembershipEntity, UUID> {

	Optional<MembershipEntity> findByTenantIdAndUserId(UUID tenantId, UUID userId);

	List<MembershipEntity> findByUserIdOrderByTenantIdAsc(UUID userId);
}
