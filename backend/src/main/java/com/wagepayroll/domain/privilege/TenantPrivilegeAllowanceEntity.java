package com.wagepayroll.domain.privilege;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.wagepayroll.domain.TenantScopedEntity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "tenant_privilege_allowance")
public class TenantPrivilegeAllowanceEntity extends TenantScopedEntity {

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "privilege_id", length = 36, nullable = false)
	private UUID privilegeId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public UUID getPrivilegeId() {
		return privilegeId;
	}

	public void setPrivilegeId(UUID privilegeId) {
		this.privilegeId = privilegeId;
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
