package com.wagepayroll.domain.ledger;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.wagepayroll.domain.TenantScopedEntity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "tenant_ledger")
public class TenantLedgerEntity extends TenantScopedEntity {

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "company_id", nullable = false, length = 36)
	private UUID companyId;

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "platform_ledger_template_id", nullable = false, length = 36)
	private UUID platformLedgerTemplateId;

	@Column(name = "code", nullable = false, length = 64)
	private String code;

	@Column(name = "description", nullable = false, length = 500)
	private String description;

	@Column(name = "active", nullable = false)
	private boolean active = true;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public UUID getCompanyId() {
		return companyId;
	}

	public void setCompanyId(UUID companyId) {
		this.companyId = companyId;
	}

	public UUID getPlatformLedgerTemplateId() {
		return platformLedgerTemplateId;
	}

	public void setPlatformLedgerTemplateId(UUID platformLedgerTemplateId) {
		this.platformLedgerTemplateId = platformLedgerTemplateId;
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}
}
