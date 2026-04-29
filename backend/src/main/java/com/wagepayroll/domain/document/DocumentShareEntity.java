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
@Table(name = "document_share")
public class DocumentShareEntity extends TenantScopedEntity {

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "document_id", length = 36, nullable = false)
	private UUID documentId;

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "grantee_user_id", length = 36)
	private UUID granteeUserId;

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "grantee_role_id", length = 36)
	private UUID granteeRoleId;

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

	public UUID getGranteeUserId() {
		return granteeUserId;
	}

	public void setGranteeUserId(UUID granteeUserId) {
		this.granteeUserId = granteeUserId;
	}

	public UUID getGranteeRoleId() {
		return granteeRoleId;
	}

	public void setGranteeRoleId(UUID granteeRoleId) {
		this.granteeRoleId = granteeRoleId;
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
