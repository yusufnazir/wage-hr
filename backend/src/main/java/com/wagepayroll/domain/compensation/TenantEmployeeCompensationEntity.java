package com.wagepayroll.domain.compensation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.wagepayroll.domain.TenantScopedEntity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "tenant_employee_compensation")
public class TenantEmployeeCompensationEntity extends TenantScopedEntity {

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "company_id", length = 36, nullable = false)
	private UUID companyId;

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "employee_id", length = 36, nullable = false)
	private UUID employeeId;

	@Column(name = "currency_code", nullable = false, length = 3, columnDefinition = "CHAR(3)")
	private String currencyCode;

	@Column(name = "wage_type", nullable = false, length = 20)
	private String wageType;

	@Column(name = "wage_amount", nullable = false, precision = 18, scale = 4)
	private BigDecimal wageAmount;

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "work_time_id", length = 36)
	private UUID workTimeId;

	@Column(name = "apply_taxes", nullable = false)
	private boolean applyTaxes;

	@Column(name = "apply_tax_exempt", nullable = false)
	private boolean applyTaxExempt;

	@Column(name = "apply_aov", nullable = false)
	private boolean applyAov;

	@Column(name = "notes", length = 500)
	private String notes;

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

	public String getCurrencyCode() {
		return currencyCode;
	}

	public void setCurrencyCode(String currencyCode) {
		this.currencyCode = currencyCode;
	}

	public String getWageType() {
		return wageType;
	}

	public void setWageType(String wageType) {
		this.wageType = wageType;
	}

	public BigDecimal getWageAmount() {
		return wageAmount;
	}

	public void setWageAmount(BigDecimal wageAmount) {
		this.wageAmount = wageAmount;
	}

	public UUID getWorkTimeId() {
		return workTimeId;
	}

	public void setWorkTimeId(UUID workTimeId) {
		this.workTimeId = workTimeId;
	}

	public boolean isApplyTaxes() {
		return applyTaxes;
	}

	public void setApplyTaxes(boolean applyTaxes) {
		this.applyTaxes = applyTaxes;
	}

	public boolean isApplyTaxExempt() {
		return applyTaxExempt;
	}

	public void setApplyTaxExempt(boolean applyTaxExempt) {
		this.applyTaxExempt = applyTaxExempt;
	}

	public boolean isApplyAov() {
		return applyAov;
	}

	public void setApplyAov(boolean applyAov) {
		this.applyAov = applyAov;
	}

	public String getNotes() {
		return notes;
	}

	public void setNotes(String notes) {
		this.notes = notes;
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
