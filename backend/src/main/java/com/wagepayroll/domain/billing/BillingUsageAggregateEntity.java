package com.wagepayroll.domain.billing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.wagepayroll.domain.AbstractUuidEntity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "billing_usage_aggregate")
public class BillingUsageAggregateEntity extends AbstractUuidEntity {

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "tenant_id", length = 36, nullable = false)
	private UUID tenantId;

	@Column(name = "metric_key", nullable = false, length = 64)
	private String metricKey;

	@Column(name = "period_start", nullable = false)
	private Instant periodStart;

	@Column(name = "period_end", nullable = false)
	private Instant periodEnd;

	@Column(name = "total_quantity", nullable = false, precision = 19, scale = 6)
	private BigDecimal totalQuantity;

	@Column(name = "last_aggregated_at", nullable = false)
	private Instant lastAggregatedAt;

	@Column(name = "external_synced", nullable = false)
	private boolean externalSynced;

	@Column(name = "external_synced_at", nullable = true)
	private Instant externalSyncedAt;

	public UUID getTenantId() {
		return tenantId;
	}

	public void setTenantId(UUID tenantId) {
		this.tenantId = tenantId;
	}

	public String getMetricKey() {
		return metricKey;
	}

	public void setMetricKey(String metricKey) {
		this.metricKey = metricKey;
	}

	public Instant getPeriodStart() {
		return periodStart;
	}

	public void setPeriodStart(Instant periodStart) {
		this.periodStart = periodStart;
	}

	public Instant getPeriodEnd() {
		return periodEnd;
	}

	public void setPeriodEnd(Instant periodEnd) {
		this.periodEnd = periodEnd;
	}

	public BigDecimal getTotalQuantity() {
		return totalQuantity;
	}

	public void setTotalQuantity(BigDecimal totalQuantity) {
		this.totalQuantity = totalQuantity;
	}

	public Instant getLastAggregatedAt() {
		return lastAggregatedAt;
	}

	public void setLastAggregatedAt(Instant lastAggregatedAt) {
		this.lastAggregatedAt = lastAggregatedAt;
	}

	public boolean isExternalSynced() {
		return externalSynced;
	}

	public void setExternalSynced(boolean externalSynced) {
		this.externalSynced = externalSynced;
	}

	public Instant getExternalSyncedAt() {
		return externalSyncedAt;
	}

	public void setExternalSyncedAt(Instant externalSyncedAt) {
		this.externalSyncedAt = externalSyncedAt;
	}
}
