package com.wagepayroll.domain.audit;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEventEntity, UUID> {

	Optional<AuditEventEntity> findFirstByActorUserIdAndActionCodeOrderByOccurredAtDesc(UUID actorUserId,
			String actionCode);
}
