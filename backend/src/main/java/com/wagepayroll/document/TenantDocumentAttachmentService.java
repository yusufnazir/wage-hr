package com.wagepayroll.document;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.wagepayroll.api.dto.DocumentAttachmentListItemDto;
import com.wagepayroll.api.dto.DocumentHubItemDto;
import com.wagepayroll.domain.document.DocumentAttachmentEntity;
import com.wagepayroll.domain.document.DocumentAttachmentRepository;
import com.wagepayroll.domain.document.TenantDocumentRepository;
import com.wagepayroll.domain.document.TenantDocumentEntity;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TenantDocumentAttachmentService {

	private final DocumentAttachmentRepository documentAttachmentRepository;
	private final TenantDocumentMutationPolicy mutationPolicy;
	private final DocumentAccessService documentAccessService;
	private final TenantDocumentRepository tenantDocumentRepository;

	public TenantDocumentAttachmentService(DocumentAttachmentRepository documentAttachmentRepository,
			TenantDocumentMutationPolicy mutationPolicy, DocumentAccessService documentAccessService,
			TenantDocumentRepository tenantDocumentRepository) {
		this.documentAttachmentRepository = documentAttachmentRepository;
		this.mutationPolicy = mutationPolicy;
		this.documentAccessService = documentAccessService;
		this.tenantDocumentRepository = tenantDocumentRepository;
	}

	@Transactional(readOnly = true)
	public List<DocumentAttachmentListItemDto> listAttachments(UUID tenantId, UUID actorUserId, UUID documentId) {
		documentAccessService.findReadableDocument(tenantId, actorUserId, documentId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND"));
		return documentAttachmentRepository.findByTenantIdAndDocumentIdOrderByCreatedAtAsc(tenantId, documentId).stream()
				.map(this::toDto).toList();
	}

	@Transactional(readOnly = true)
	public List<DocumentHubItemDto> listByEntity(UUID tenantId, String entityType, UUID entityId) {
		List<UUID> docIds = documentAttachmentRepository
				.findByTenantIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(tenantId, entityType, entityId).stream()
				.map(DocumentAttachmentEntity::getDocumentId).toList();
		if (docIds.isEmpty()) return List.of();
		return tenantDocumentRepository.findByTenantIdAndIdInAndDeletedAtIsNullOrderByCreatedAtDesc(tenantId, docIds).stream()
				.map(d -> new DocumentHubItemDto(d.getId().toString(), d.getOriginalFilename(), d.getContentType(),
						d.getSizeBytes(), d.getCreatedAt(), "entity"))
				.toList();
	}

	@Transactional
	public DocumentAttachmentListItemDto createAttachment(UUID tenantId, UUID actorUserId, UUID documentId, String entityType,
			UUID entityId) {
		TenantDocumentEntity doc = mutationPolicy.requireUploaderDocument(tenantId, actorUserId, documentId);
		if (documentAttachmentRepository.existsByTenantIdAndDocumentIdAndEntityTypeAndEntityId(tenantId, documentId, entityType,
				entityId)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "ATTACHMENT_ALREADY_EXISTS");
		}
		Instant now = Instant.now();
		DocumentAttachmentEntity e = new DocumentAttachmentEntity();
		e.setId(UUID.randomUUID());
		e.setTenantId(tenantId);
		e.setDocumentId(doc.getId());
		e.setEntityType(entityType);
		e.setEntityId(entityId);
		e.setCreatedByUserId(actorUserId);
		e.setCreatedAt(now);
		return toDto(documentAttachmentRepository.save(e));
	}

	@Transactional
	public void deleteAttachment(UUID tenantId, UUID actorUserId, UUID documentId, UUID attachmentId) {
		mutationPolicy.requireUploaderDocument(tenantId, actorUserId, documentId);
		DocumentAttachmentEntity row = documentAttachmentRepository.findByIdAndTenantIdAndDocumentId(attachmentId, tenantId, documentId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ATTACHMENT_NOT_FOUND"));
		documentAttachmentRepository.delete(row);
	}

	private DocumentAttachmentListItemDto toDto(DocumentAttachmentEntity e) {
		return new DocumentAttachmentListItemDto(e.getId().toString(), e.getEntityType(), e.getEntityId().toString(), e.getCreatedByUserId().toString(),
				e.getCreatedAt());
	}
}
