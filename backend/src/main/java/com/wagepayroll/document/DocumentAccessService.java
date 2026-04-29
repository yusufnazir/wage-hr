package com.wagepayroll.document;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.wagepayroll.domain.document.DocumentShareRepository;
import com.wagepayroll.domain.document.TenantDocumentEntity;
import com.wagepayroll.domain.document.TenantDocumentRepository;
import com.wagepayroll.domain.role.UserRoleRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentAccessService {

	private final TenantDocumentRepository tenantDocumentRepository;
	private final DocumentShareRepository documentShareRepository;
	private final UserRoleRepository userRoleRepository;

	public DocumentAccessService(TenantDocumentRepository tenantDocumentRepository,
			DocumentShareRepository documentShareRepository, UserRoleRepository userRoleRepository) {
		this.tenantDocumentRepository = tenantDocumentRepository;
		this.documentShareRepository = documentShareRepository;
		this.userRoleRepository = userRoleRepository;
	}

	@Transactional(readOnly = true)
	public Optional<TenantDocumentEntity> findReadableDocument(UUID tenantId, UUID userId, UUID documentId) {
		Optional<TenantDocumentEntity> docOpt = tenantDocumentRepository.findById(documentId);
		if (docOpt.isEmpty()) {
			return Optional.empty();
		}
		TenantDocumentEntity d = docOpt.get();
		if (!tenantId.equals(d.getTenantId()) || d.getDeletedAt() != null) {
			return Optional.empty();
		}
		if (userId.equals(d.getUploadedByUserId())) {
			return Optional.of(d);
		}
		if (documentShareRepository.existsByTenantIdAndDocumentIdAndGranteeUserId(tenantId, documentId, userId)) {
			return Optional.of(d);
		}
		List<UUID> roleIds = userRoleRepository.findRoleIdsByUserAndTenant(userId, tenantId);
		if (!roleIds.isEmpty()
				&& documentShareRepository.existsByTenantIdAndDocumentIdAndGranteeRoleIdIn(tenantId, documentId, roleIds)) {
			return Optional.of(d);
		}
		return Optional.empty();
	}
}
