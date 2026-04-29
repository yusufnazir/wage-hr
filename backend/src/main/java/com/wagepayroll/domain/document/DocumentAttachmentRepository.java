package com.wagepayroll.domain.document;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentAttachmentRepository extends JpaRepository<DocumentAttachmentEntity, UUID> {

	List<DocumentAttachmentEntity> findByTenantIdAndDocumentIdOrderByCreatedAtAsc(UUID tenantId, UUID documentId);

	Optional<DocumentAttachmentEntity> findByIdAndTenantIdAndDocumentId(UUID id, UUID tenantId, UUID documentId);

	boolean existsByTenantIdAndDocumentIdAndEntityTypeAndEntityId(UUID tenantId, UUID documentId, String entityType,
			UUID entityId);
}
