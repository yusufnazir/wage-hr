package com.wagepayroll.domain.wagecomponent;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.wagepayroll.domain.AbstractUuidEntity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "tenant_wage_component_dependency")
public class TenantWageComponentDependencyEntity extends AbstractUuidEntity {

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "tenant_id", length = 36, nullable = false)
	private UUID tenantId;

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "tenant_wage_component_id", length = 36, nullable = false)
	private UUID tenantWageComponentId;

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "depends_on_tenant_wage_component_id", length = 36, nullable = false)
	private UUID dependsOnTenantWageComponentId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public UUID getTenantId() {
		return tenantId;
	}

	public void setTenantId(UUID tenantId) {
		this.tenantId = tenantId;
	}

	public UUID getTenantWageComponentId() {
		return tenantWageComponentId;
	}

	public void setTenantWageComponentId(UUID tenantWageComponentId) {
		this.tenantWageComponentId = tenantWageComponentId;
	}

	public UUID getDependsOnTenantWageComponentId() {
		return dependsOnTenantWageComponentId;
	}

	public void setDependsOnTenantWageComponentId(UUID dependsOnTenantWageComponentId) {
		this.dependsOnTenantWageComponentId = dependsOnTenantWageComponentId;
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
