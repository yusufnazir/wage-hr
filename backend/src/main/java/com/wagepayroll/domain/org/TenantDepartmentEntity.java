package com.wagepayroll.domain.org;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.wagepayroll.domain.TenantScopedEntity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "tenant_department")
public class TenantDepartmentEntity extends TenantScopedEntity {

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "company_id", length = 36, nullable = false)
	private UUID companyId;

	@Column(name = "name", nullable = false, length = 120)
	private String name;

	@Column(name = "code", nullable = false, length = 40)
	private String code;

	@Column(name = "description", length = 500)
	private String description;

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "parent_department_id", length = 36)
	private UUID parentDepartmentId;

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "manager_employee_id", length = 36)
	private UUID managerEmployeeId;

	@Column(name = "active", nullable = false)
	private boolean active;

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

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
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

	public UUID getParentDepartmentId() {
		return parentDepartmentId;
	}

	public void setParentDepartmentId(UUID parentDepartmentId) {
		this.parentDepartmentId = parentDepartmentId;
	}

	public UUID getManagerEmployeeId() {
		return managerEmployeeId;
	}

	public void setManagerEmployeeId(UUID managerEmployeeId) {
		this.managerEmployeeId = managerEmployeeId;
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
