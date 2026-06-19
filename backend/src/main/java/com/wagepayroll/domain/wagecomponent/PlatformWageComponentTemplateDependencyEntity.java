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
@Table(name = "platform_wage_component_template_dependency")
public class PlatformWageComponentTemplateDependencyEntity extends AbstractUuidEntity {

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "platform_wage_component_template_id", length = 36, nullable = false)
	private UUID platformWageComponentTemplateId;

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "depends_on_template_id", length = 36, nullable = false)
	private UUID dependsOnTemplateId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public UUID getPlatformWageComponentTemplateId() {
		return platformWageComponentTemplateId;
	}

	public void setPlatformWageComponentTemplateId(UUID platformWageComponentTemplateId) {
		this.platformWageComponentTemplateId = platformWageComponentTemplateId;
	}

	public UUID getDependsOnTemplateId() {
		return dependsOnTemplateId;
	}

	public void setDependsOnTemplateId(UUID dependsOnTemplateId) {
		this.dependsOnTemplateId = dependsOnTemplateId;
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
