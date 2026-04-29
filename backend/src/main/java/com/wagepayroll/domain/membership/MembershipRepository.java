package com.wagepayroll.domain.membership;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MembershipRepository extends JpaRepository<MembershipEntity, UUID> {

	Optional<MembershipEntity> findByTenantIdAndUserId(UUID tenantId, UUID userId);

	List<MembershipEntity> findByUserIdOrderByTenantIdAsc(UUID userId);

	@Modifying
	@Query("""
			update MembershipEntity m set m.lastActiveAt = :ts, m.updatedAt = :ts
			where m.tenantId = :tenantId and m.userId = :userId
			and (m.lastActiveAt is null or m.lastActiveAt < :threshold)
			""")
	int touchLastActiveIfStale(@Param("tenantId") UUID tenantId, @Param("userId") UUID userId, @Param("ts") Instant ts,
			@Param("threshold") Instant threshold);
}
