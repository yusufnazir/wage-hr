package com.wagepayroll.domain.componentgroup;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.wagepayroll.domain.AbstractUuidEntity;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentEntity;

@Entity
@Table(name = "tenant_component_item")
public class TenantComponentItemEntity extends AbstractUuidEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tenant_component_header_id", nullable = false)
	private TenantComponentHeaderEntity header;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tenant_wage_component_id", nullable = false)
	private TenantWageComponentEntity wageComponent;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public TenantComponentHeaderEntity getHeader() {
		return header;
	}

	public void setHeader(TenantComponentHeaderEntity header) {
		this.header = header;
	}

	public UUID getTenantComponentHeaderId() {
		return header == null ? null : header.getId();
	}

	public TenantWageComponentEntity getWageComponent() {
		return wageComponent;
	}

	public void setWageComponent(TenantWageComponentEntity wageComponent) {
		this.wageComponent = wageComponent;
	}

	public UUID getTenantWageComponentId() {
		return wageComponent == null ? null : wageComponent.getId();
	}

	public int getSortOrder() {
		return sortOrder;
	}

	public void setSortOrder(int sortOrder) {
		this.sortOrder = sortOrder;
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
