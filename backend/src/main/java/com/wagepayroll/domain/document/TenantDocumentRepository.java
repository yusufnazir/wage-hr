package com.wagepayroll.domain.document;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantDocumentRepository extends JpaRepository<TenantDocumentEntity, UUID> {

	List<TenantDocumentEntity> findByTenantIdAndUploadedByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID tenantId,
			UUID uploadedByUserId);

	List<TenantDocumentEntity> findByTenantIdAndIdInAndDeletedAtIsNullOrderByCreatedAtDesc(UUID tenantId,
			Collection<UUID> ids);

	boolean existsByTenantIdAndStorageKey(UUID tenantId, String storageKey);

	List<TenantDocumentEntity> findByDeletedAtIsNotNullAndDeletedAtBeforeOrderByDeletedAtAsc(Instant cutoff,
			Pageable pageable);
}
