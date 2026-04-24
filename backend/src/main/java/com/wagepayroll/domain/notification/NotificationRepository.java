package com.wagepayroll.domain.notification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {

	@Query(value = "SELECT * FROM notification WHERE tenant_id = ?1 AND recipient_user_id = ?2 ORDER BY created_at DESC, id DESC LIMIT ?3 OFFSET ?4", nativeQuery = true)
	List<NotificationEntity> findSliceDesc(String tenantId, String recipientUserId, int limit, int offset);

	long countByTenantIdAndRecipientUserId(UUID tenantId, UUID recipientUserId);

	Optional<NotificationEntity> findByIdAndTenantIdAndRecipientUserId(UUID id, UUID tenantId, UUID recipientUserId);
}
