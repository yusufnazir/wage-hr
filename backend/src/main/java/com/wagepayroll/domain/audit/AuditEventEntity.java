package com.wagepayroll.domain.audit;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.wagepayroll.domain.AbstractUuidEntity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_event")
public class AuditEventEntity extends AbstractUuidEntity {

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "tenant_id", length = 36, nullable = true)
	private UUID tenantId;

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "actor_user_id", length = 36, nullable = true)
	private UUID actorUserId;

	@Column(name = "action_code", nullable = false, length = 128)
	private String actionCode;

	@Column(name = "resource_type", nullable = false, length = 64)
	private String resourceType;

	@Column(name = "resource_id", length = 64)
	private String resourceId;

	@Column(name = "correlation_id", length = 128)
	private String correlationId;

	@Column(name = "metadata_json", length = 2000)
	private String metadataJson;

	public Instant getOccurredAt() {
		return occurredAt;
	}

	public void setOccurredAt(Instant occurredAt) {
		this.occurredAt = occurredAt;
	}

	public UUID getTenantId() {
		return tenantId;
	}

	public void setTenantId(UUID tenantId) {
		this.tenantId = tenantId;
	}

	public UUID getActorUserId() {
		return actorUserId;
	}

	public void setActorUserId(UUID actorUserId) {
		this.actorUserId = actorUserId;
	}

	public String getActionCode() {
		return actionCode;
	}

	public void setActionCode(String actionCode) {
		this.actionCode = actionCode;
	}

	public String getResourceType() {
		return resourceType;
	}

	public void setResourceType(String resourceType) {
		this.resourceType = resourceType;
	}

	public String getResourceId() {
		return resourceId;
	}

	public void setResourceId(String resourceId) {
		this.resourceId = resourceId;
	}

	public String getCorrelationId() {
		return correlationId;
	}

	public void setCorrelationId(String correlationId) {
		this.correlationId = correlationId;
	}

	public String getMetadataJson() {
		return metadataJson;
	}

	public void setMetadataJson(String metadataJson) {
		this.metadataJson = metadataJson;
	}
}
