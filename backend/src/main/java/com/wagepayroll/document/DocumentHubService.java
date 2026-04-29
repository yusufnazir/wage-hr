package com.wagepayroll.document;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.wagepayroll.api.dto.DocumentHubItemDto;
import com.wagepayroll.domain.document.DocumentShareRepository;
import com.wagepayroll.domain.document.TenantDocumentEntity;
import com.wagepayroll.domain.document.TenantDocumentRepository;
import com.wagepayroll.domain.role.UserRoleRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentHubService {

	private final TenantDocumentRepository tenantDocumentRepository;
	private final DocumentShareRepository documentShareRepository;
	private final UserRoleRepository userRoleRepository;

	public DocumentHubService(TenantDocumentRepository tenantDocumentRepository,
			DocumentShareRepository documentShareRepository, UserRoleRepository userRoleRepository) {
		this.tenantDocumentRepository = tenantDocumentRepository;
		this.documentShareRepository = documentShareRepository;
		this.userRoleRepository = userRoleRepository;
	}

	@Transactional(readOnly = true)
	public List<DocumentHubItemDto> hub(UUID tenantId, UUID userId) {
		List<TenantDocumentEntity> owned = tenantDocumentRepository
				.findByTenantIdAndUploadedByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(tenantId, userId);

		List<UUID> roleIds = userRoleRepository.findRoleIdsByUserAndTenant(userId, tenantId);
		Set<UUID> sharedIds = new HashSet<>();
		sharedIds.addAll(documentShareRepository.findDocumentIdsSharedToUser(tenantId, userId));
		if (!roleIds.isEmpty()) {
			sharedIds.addAll(documentShareRepository.findDocumentIdsSharedToRoles(tenantId, roleIds));
		}

		List<TenantDocumentEntity> sharedDocs = sharedIds.isEmpty()
				? List.of()
				: tenantDocumentRepository.findByTenantIdAndIdInAndDeletedAtIsNullOrderByCreatedAtDesc(tenantId, sharedIds);

		Map<UUID, HubRow> byId = new HashMap<>();
		for (TenantDocumentEntity d : owned) {
			byId.put(d.getId(), new HubRow(d, "OWNED"));
		}
		for (TenantDocumentEntity d : sharedDocs) {
			if (userId.equals(d.getUploadedByUserId())) {
				byId.putIfAbsent(d.getId(), new HubRow(d, "OWNED"));
			}
			else {
				byId.putIfAbsent(d.getId(), new HubRow(d, "SHARED"));
			}
		}

		List<HubRow> rows = new ArrayList<>(byId.values());
		rows.sort(Comparator.comparing(r -> r.doc().getCreatedAt(), Comparator.reverseOrder()));

		List<DocumentHubItemDto> out = new ArrayList<>(rows.size());
		for (HubRow r : rows) {
			TenantDocumentEntity d = r.doc();
			out.add(new DocumentHubItemDto(d.getId().toString(), d.getOriginalFilename(), d.getContentType(), d.getSizeBytes(),
					d.getCreatedAt(), r.hubSource()));
		}
		return out;
	}

	private record HubRow(TenantDocumentEntity doc, String hubSource) {
	}
}
