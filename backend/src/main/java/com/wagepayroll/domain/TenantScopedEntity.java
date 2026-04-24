package com.wagepayroll.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@MappedSuperclass
public abstract class TenantScopedEntity extends AbstractUuidEntity {

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "tenant_id", length = 36, nullable = false)
	private UUID tenantId;

	public UUID getTenantId() {
		return tenantId;
	}

	public void setTenantId(UUID tenantId) {
		this.tenantId = tenantId;
	}
}
