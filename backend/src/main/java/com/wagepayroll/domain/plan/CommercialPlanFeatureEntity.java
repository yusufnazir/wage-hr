package com.wagepayroll.domain.plan;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.wagepayroll.domain.AbstractUuidEntity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "commercial_plan_feature")
public class CommercialPlanFeatureEntity extends AbstractUuidEntity {

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "commercial_plan_id", length = 36, nullable = false)
	private UUID commercialPlanId;

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "plan_feature_id", length = 36, nullable = false)
	private UUID planFeatureId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public UUID getCommercialPlanId() {
		return commercialPlanId;
	}

	public void setCommercialPlanId(UUID commercialPlanId) {
		this.commercialPlanId = commercialPlanId;
	}

	public UUID getPlanFeatureId() {
		return planFeatureId;
	}

	public void setPlanFeatureId(UUID planFeatureId) {
		this.planFeatureId = planFeatureId;
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
