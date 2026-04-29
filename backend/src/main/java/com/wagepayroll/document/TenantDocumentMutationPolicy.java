package com.wagepayroll.document;

import java.util.UUID;

import com.wagepayroll.domain.document.TenantDocumentEntity;
import com.wagepayroll.domain.document.TenantDocumentRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class TenantDocumentMutationPolicy {

	private final TenantDocumentRepository tenantDocumentRepository;

	public TenantDocumentMutationPolicy(TenantDocumentRepository tenantDocumentRepository) {
		this.tenantDocumentRepository = tenantDocumentRepository;
	}

	/**
	 * v1: only the original uploader may create/delete shares and attachments (see {@code document-sharing.md}).
	 */
	public TenantDocumentEntity requireUploaderDocument(UUID tenantId, UUID actorUserId, UUID documentId) {
		TenantDocumentEntity doc = tenantDocumentRepository.findById(documentId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND"));
		if (!tenantId.equals(doc.getTenantId())) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND");
		}
		if (doc.getDeletedAt() != null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND");
		}
		if (!actorUserId.equals(doc.getUploadedByUserId())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "NOT_DOCUMENT_UPLOADER");
		}
		return doc;
	}
}
