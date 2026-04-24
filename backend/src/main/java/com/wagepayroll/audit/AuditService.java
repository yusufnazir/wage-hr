package com.wagepayroll.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wagepayroll.domain.audit.AuditEventEntity;
import com.wagepayroll.domain.audit.AuditEventRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

	private final AuditEventRepository auditEventRepository;
	private final ObjectMapper objectMapper;

	public AuditService(AuditEventRepository auditEventRepository, ObjectMapper objectMapper) {
		this.auditEventRepository = auditEventRepository;
		this.objectMapper = objectMapper;
	}

	/**
	 * Append-only audit row. {@code metadata} must not contain PII beyond what the module allows.
	 */
	@Transactional
	public void append(UUID tenantId, UUID actorUserId, String actionCode, String resourceType, String resourceId,
			String correlationId, Map<String, Object> metadata) {
		AuditEventEntity e = new AuditEventEntity();
		e.setId(UUID.randomUUID());
		e.setOccurredAt(Instant.now());
		e.setTenantId(tenantId);
		e.setActorUserId(actorUserId);
		e.setActionCode(actionCode);
		e.setResourceType(resourceType);
		e.setResourceId(resourceId);
		e.setCorrelationId(correlationId);
		if (metadata != null && !metadata.isEmpty()) {
			try {
				e.setMetadataJson(objectMapper.writeValueAsString(metadata));
			}
			catch (JsonProcessingException ex) {
				throw new IllegalStateException("audit metadata serialization", ex);
			}
		}
		auditEventRepository.save(e);
	}
}
