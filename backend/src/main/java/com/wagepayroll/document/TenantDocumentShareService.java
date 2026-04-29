package com.wagepayroll.document;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.wagepayroll.api.dto.DocumentShareListItemDto;
import com.wagepayroll.domain.document.DocumentShareEntity;
import com.wagepayroll.domain.document.DocumentShareRepository;
import com.wagepayroll.domain.document.TenantDocumentEntity;
import com.wagepayroll.domain.membership.MembershipRepository;
import com.wagepayroll.domain.role.RoleRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TenantDocumentShareService {

	private final DocumentShareRepository documentShareRepository;
	private final TenantDocumentMutationPolicy mutationPolicy;
	private final MembershipRepository membershipRepository;
	private final RoleRepository roleRepository;

	public TenantDocumentShareService(DocumentShareRepository documentShareRepository,
			TenantDocumentMutationPolicy mutationPolicy, MembershipRepository membershipRepository, RoleRepository roleRepository) {
		this.documentShareRepository = documentShareRepository;
		this.mutationPolicy = mutationPolicy;
		this.membershipRepository = membershipRepository;
		this.roleRepository = roleRepository;
	}

	@Transactional(readOnly = true)
	public List<DocumentShareListItemDto> listShares(UUID tenantId, UUID actorUserId, UUID documentId) {
		mutationPolicy.requireUploaderDocument(tenantId, actorUserId, documentId);
		return documentShareRepository.findByTenantIdAndDocumentIdOrderByCreatedAtAsc(tenantId, documentId).stream()
				.map(this::toDto).toList();
	}

	@Transactional
	public DocumentShareListItemDto createShare(UUID tenantId, UUID actorUserId, UUID documentId, UUID granteeUserId,
			UUID granteeRoleId) {
		TenantDocumentEntity doc = mutationPolicy.requireUploaderDocument(tenantId, actorUserId, documentId);
		boolean userTarget = granteeUserId != null;
		boolean roleTarget = granteeRoleId != null;
		if (userTarget == roleTarget) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SHARE_TARGET_XOR");
		}
		if (userTarget) {
			membershipRepository.findByTenantIdAndUserId(tenantId, granteeUserId)
					.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "GRANTEE_NOT_IN_TENANT"));
			if (documentShareRepository.existsByTenantIdAndDocumentIdAndGranteeUserId(tenantId, documentId, granteeUserId)) {
				throw new ResponseStatusException(HttpStatus.CONFLICT, "SHARE_ALREADY_EXISTS");
			}
		}
		else {
			roleRepository.findByTenantIdAndId(tenantId, granteeRoleId)
					.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "ROLE_NOT_IN_TENANT"));
			if (documentShareRepository.existsByTenantIdAndDocumentIdAndGranteeRoleId(tenantId, documentId, granteeRoleId)) {
				throw new ResponseStatusException(HttpStatus.CONFLICT, "SHARE_ALREADY_EXISTS");
			}
		}
		Instant now = Instant.now();
		DocumentShareEntity e = new DocumentShareEntity();
		e.setId(UUID.randomUUID());
		e.setTenantId(tenantId);
		e.setDocumentId(doc.getId());
		e.setGranteeUserId(userTarget ? granteeUserId : null);
		e.setGranteeRoleId(roleTarget ? granteeRoleId : null);
		e.setCreatedByUserId(actorUserId);
		e.setCreatedAt(now);
		return toDto(documentShareRepository.save(e));
	}

	@Transactional
	public void deleteShare(UUID tenantId, UUID actorUserId, UUID documentId, UUID shareId) {
		mutationPolicy.requireUploaderDocument(tenantId, actorUserId, documentId);
		DocumentShareEntity share = documentShareRepository.findByIdAndTenantIdAndDocumentId(shareId, tenantId, documentId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "SHARE_NOT_FOUND"));
		documentShareRepository.delete(share);
	}

	private DocumentShareListItemDto toDto(DocumentShareEntity e) {
		return new DocumentShareListItemDto(e.getId().toString(), e.getGranteeUserId() == null ? null : e.getGranteeUserId().toString(),
				e.getGranteeRoleId() == null ? null : e.getGranteeRoleId().toString(), e.getCreatedByUserId().toString(), e.getCreatedAt());
	}
}
