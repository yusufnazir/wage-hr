package com.wagepayroll.domain.payrollbase;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;

import com.wagepayroll.domain.AbstractUuidEntity;
import com.wagepayroll.payroll.model.PayrollBaseEffectCalculationType;
import com.wagepayroll.payroll.model.PayrollBaseEffectDirection;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@MappedSuperclass
public abstract class WageComponentBaseEffectColumns extends AbstractUuidEntity {

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "platform_payroll_base_id", length = 36, nullable = false)
	private UUID platformPayrollBaseId;

	@Enumerated(EnumType.STRING)
	@Column(name = "effect_direction", nullable = false, length = 20)
	private PayrollBaseEffectDirection effectDirection;

	@Enumerated(EnumType.STRING)
	@Column(name = "effect_calculation_type", nullable = false, length = 20)
	private PayrollBaseEffectCalculationType effectCalculationType;

	@Column(name = "effect_value", precision = 18, scale = 6)
	private BigDecimal effectValue;

	@Column(name = "priority", nullable = false)
	private int priority;

	@Column(name = "effective_from")
	private LocalDate effectiveFrom;

	@Column(name = "effective_until")
	private LocalDate effectiveUntil;

	@Column(name = "active", nullable = false)
	private boolean active = true;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public UUID getPlatformPayrollBaseId() {
		return platformPayrollBaseId;
	}

	public void setPlatformPayrollBaseId(UUID platformPayrollBaseId) {
		this.platformPayrollBaseId = platformPayrollBaseId;
	}

	public PayrollBaseEffectDirection getEffectDirection() {
		return effectDirection;
	}

	public void setEffectDirection(PayrollBaseEffectDirection effectDirection) {
		this.effectDirection = effectDirection;
	}

	public PayrollBaseEffectCalculationType getEffectCalculationType() {
		return effectCalculationType;
	}

	public void setEffectCalculationType(PayrollBaseEffectCalculationType effectCalculationType) {
		this.effectCalculationType = effectCalculationType;
	}

	public BigDecimal getEffectValue() {
		return effectValue;
	}

	public void setEffectValue(BigDecimal effectValue) {
		this.effectValue = effectValue;
	}

	public int getPriority() {
		return priority;
	}

	public void setPriority(int priority) {
		this.priority = priority;
	}

	public LocalDate getEffectiveFrom() {
		return effectiveFrom;
	}

	public void setEffectiveFrom(LocalDate effectiveFrom) {
		this.effectiveFrom = effectiveFrom;
	}

	public LocalDate getEffectiveUntil() {
		return effectiveUntil;
	}

	public void setEffectiveUntil(LocalDate effectiveUntil) {
		this.effectiveUntil = effectiveUntil;
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
