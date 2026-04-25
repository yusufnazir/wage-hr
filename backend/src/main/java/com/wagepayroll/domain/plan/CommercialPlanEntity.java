package com.wagepayroll.domain.plan;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.wagepayroll.domain.AbstractUuidEntity;

@Entity
@Table(name = "commercial_plan")
public class CommercialPlanEntity extends AbstractUuidEntity {

	@Column(name = "code", nullable = false, length = 64, unique = true)
	private String code;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	@Column(name = "active", nullable = false)
	private boolean active;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "stripe_subscription_price_id", nullable = true, length = 255)
	private String stripeSubscriptionPriceId;

	@Column(name = "paypal_billing_plan_id", nullable = true, length = 128)
	private String paypalBillingPlanId;

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public int getSortOrder() {
		return sortOrder;
	}

	public void setSortOrder(int sortOrder) {
		this.sortOrder = sortOrder;
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

	public String getStripeSubscriptionPriceId() {
		return stripeSubscriptionPriceId;
	}

	public void setStripeSubscriptionPriceId(String stripeSubscriptionPriceId) {
		this.stripeSubscriptionPriceId = stripeSubscriptionPriceId;
	}

	public String getPaypalBillingPlanId() {
		return paypalBillingPlanId;
	}

	public void setPaypalBillingPlanId(String paypalBillingPlanId) {
		this.paypalBillingPlanId = paypalBillingPlanId;
	}
}
