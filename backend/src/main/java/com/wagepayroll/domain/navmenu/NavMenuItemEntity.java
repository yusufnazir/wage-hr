package com.wagepayroll.domain.navmenu;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.wagepayroll.domain.TenantScopedEntity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "nav_menu_item")
public class NavMenuItemEntity extends TenantScopedEntity {

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "parent_id", length = 36, nullable = true)
	private UUID parentId;

	@Column(name = "path", nullable = false, length = 512)
	private String path;

	@Column(name = "label_key", nullable = false, length = 128)
	private String labelKey;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	@Column(name = "required_privilege_code", length = 128)
	private String requiredPrivilegeCode;

	@Column(name = "required_plan_feature_code", length = 64)
	private String requiredPlanFeatureCode;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public UUID getParentId() {
		return parentId;
	}

	public void setParentId(UUID parentId) {
		this.parentId = parentId;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public String getLabelKey() {
		return labelKey;
	}

	public void setLabelKey(String labelKey) {
		this.labelKey = labelKey;
	}

	public int getSortOrder() {
		return sortOrder;
	}

	public void setSortOrder(int sortOrder) {
		this.sortOrder = sortOrder;
	}

	public String getRequiredPrivilegeCode() {
		return requiredPrivilegeCode;
	}

	public void setRequiredPrivilegeCode(String requiredPrivilegeCode) {
		this.requiredPrivilegeCode = requiredPrivilegeCode;
	}

	public String getRequiredPlanFeatureCode() {
		return requiredPlanFeatureCode;
	}

	public void setRequiredPlanFeatureCode(String requiredPlanFeatureCode) {
		this.requiredPlanFeatureCode = requiredPlanFeatureCode;
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
