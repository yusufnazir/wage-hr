package com.wagepayroll.domain.document;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.wagepayroll.domain.TenantScopedEntity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "document_attachment")
public class DocumentAttachmentEntity extends TenantScopedEntity {

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "document_id", length = 36, nullable = false)
	private UUID documentId;

	@Column(name = "entity_type", nullable = false, length = 64)
	private String entityType;

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "entity_id", length = 36, nullable = false)
	private UUID entityId;

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "created_by_user_id", length = 36, nullable = false)
	private UUID createdByUserId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	public UUID getDocumentId() {
		return documentId;
	}

	public void setDocumentId(UUID documentId) {
		this.documentId = documentId;
	}

	public String getEntityType() {
		return entityType;
	}

	public void setEntityType(String entityType) {
		this.entityType = entityType;
	}

	public UUID getEntityId() {
		return entityId;
	}

	public void setEntityId(UUID entityId) {
		this.entityId = entityId;
	}

	public UUID getCreatedByUserId() {
		return createdByUserId;
	}

	public void setCreatedByUserId(UUID createdByUserId) {
		this.createdByUserId = createdByUserId;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}
}
