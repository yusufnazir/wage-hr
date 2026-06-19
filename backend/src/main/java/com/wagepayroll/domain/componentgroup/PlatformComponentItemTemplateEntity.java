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
import com.wagepayroll.domain.wagecomponent.PlatformWageComponentTemplateEntity;

@Entity
@Table(name = "platform_component_item_template")
public class PlatformComponentItemTemplateEntity extends AbstractUuidEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "platform_component_header_template_id", nullable = false)
	private PlatformComponentHeaderTemplateEntity header;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "platform_wage_component_template_id", nullable = false)
	private PlatformWageComponentTemplateEntity wageComponentTemplate;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public PlatformComponentHeaderTemplateEntity getHeader() {
		return header;
	}

	public void setHeader(PlatformComponentHeaderTemplateEntity header) {
		this.header = header;
	}

	public UUID getPlatformComponentHeaderTemplateId() {
		return header == null ? null : header.getId();
	}

	public PlatformWageComponentTemplateEntity getWageComponentTemplate() {
		return wageComponentTemplate;
	}

	public void setWageComponentTemplate(PlatformWageComponentTemplateEntity wageComponentTemplate) {
		this.wageComponentTemplate = wageComponentTemplate;
	}

	public UUID getPlatformWageComponentTemplateId() {
		return wageComponentTemplate == null ? null : wageComponentTemplate.getId();
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
