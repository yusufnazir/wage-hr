package com.wagepayroll.domain.payrollstanding;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.wagepayroll.domain.TenantScopedEntity;
import com.wagepayroll.payroll.model.StandingInstructionRecurrence;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "tenant_employee_payroll_standing_instruction")
public class TenantEmployeePayrollStandingInstructionEntity extends TenantScopedEntity {

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "company_id", length = 36, nullable = false)
	private UUID companyId;

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "employee_id", length = 36, nullable = false)
	private UUID employeeId;

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "tenant_wage_component_id", length = 36, nullable = false)
	private UUID tenantWageComponentId;

	@Column(name = "effective_from", nullable = false)
	private LocalDate effectiveFrom;

	@Column(name = "effective_to")
	private LocalDate effectiveTo;

	@Column(name = "amount", precision = 19, scale = 4)
	private BigDecimal amount;

	@Column(name = "quantity", precision = 19, scale = 4)
	private BigDecimal quantity;

	@Column(name = "rate", precision = 19, scale = 4)
	private BigDecimal rate;

	@Enumerated(EnumType.STRING)
	@Column(name = "recurrence", nullable = false, length = 40)
	private StandingInstructionRecurrence recurrence;

	@Column(name = "active", nullable = false)
	private boolean active;

	@Column(name = "amount_override", nullable = false)
	private boolean amountOverride;

	@Column(name = "factor_override", nullable = false)
	private boolean factorOverride;

	@Column(name = "remarks", length = 500)
	private String remarks;

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

	public UUID getEmployeeId() {
		return employeeId;
	}

	public void setEmployeeId(UUID employeeId) {
		this.employeeId = employeeId;
	}

	public UUID getTenantWageComponentId() {
		return tenantWageComponentId;
	}

	public void setTenantWageComponentId(UUID tenantWageComponentId) {
		this.tenantWageComponentId = tenantWageComponentId;
	}

	public LocalDate getEffectiveFrom() {
		return effectiveFrom;
	}

	public void setEffectiveFrom(LocalDate effectiveFrom) {
		this.effectiveFrom = effectiveFrom;
	}

	public LocalDate getEffectiveTo() {
		return effectiveTo;
	}

	public void setEffectiveTo(LocalDate effectiveTo) {
		this.effectiveTo = effectiveTo;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}

	public BigDecimal getQuantity() {
		return quantity;
	}

	public void setQuantity(BigDecimal quantity) {
		this.quantity = quantity;
	}

	public BigDecimal getRate() {
		return rate;
	}

	public void setRate(BigDecimal rate) {
		this.rate = rate;
	}

	public StandingInstructionRecurrence getRecurrence() {
		return recurrence;
	}

	public void setRecurrence(StandingInstructionRecurrence recurrence) {
		this.recurrence = recurrence;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public boolean isAmountOverride() {
		return amountOverride;
	}

	public void setAmountOverride(boolean amountOverride) {
		this.amountOverride = amountOverride;
	}

	public boolean isFactorOverride() {
		return factorOverride;
	}

	public void setFactorOverride(boolean factorOverride) {
		this.factorOverride = factorOverride;
	}

	public String getRemarks() {
		return remarks;
	}

	public void setRemarks(String remarks) {
		this.remarks = remarks;
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
