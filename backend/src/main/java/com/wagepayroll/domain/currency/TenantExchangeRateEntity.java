package com.wagepayroll.domain.currency;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.wagepayroll.domain.TenantScopedEntity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "tenant_exchange_rate")
public class TenantExchangeRateEntity extends TenantScopedEntity {

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "from_currency_id", length = 36, nullable = false)
	private UUID fromCurrencyId;

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "to_currency_id", length = 36, nullable = false)
	private UUID toCurrencyId;

	@Column(name = "rate", precision = 18, scale = 8, nullable = false)
	private BigDecimal rate;

	@Column(name = "effective_date", nullable = false)
	private LocalDate effectiveDate;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public UUID getFromCurrencyId() {
		return fromCurrencyId;
	}

	public void setFromCurrencyId(UUID fromCurrencyId) {
		this.fromCurrencyId = fromCurrencyId;
	}

	public UUID getToCurrencyId() {
		return toCurrencyId;
	}

	public void setToCurrencyId(UUID toCurrencyId) {
		this.toCurrencyId = toCurrencyId;
	}

	public BigDecimal getRate() {
		return rate;
	}

	public void setRate(BigDecimal rate) {
		this.rate = rate;
	}

	public LocalDate getEffectiveDate() {
		return effectiveDate;
	}

	public void setEffectiveDate(LocalDate effectiveDate) {
		this.effectiveDate = effectiveDate;
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