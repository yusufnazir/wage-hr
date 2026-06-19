package com.wagepayroll.domain.wagecomponent;

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
@Table(name = "tenant_wage_component_transaction")
public class TenantWageComponentTransactionEntity extends TenantScopedEntity {

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "company_id", length = 36, nullable = false)
	private UUID companyId;

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "employee_id", length = 36, nullable = false)
	private UUID employeeId;

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "pay_period_id", length = 36, nullable = false)
	private UUID payPeriodId;

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "pay_period_run_id", length = 36)
	private UUID payPeriodRunId;

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "tenant_wage_component_id", length = 36, nullable = false)
	private UUID tenantWageComponentId;

	@Column(name = "quantity", precision = 19, scale = 4)
	private BigDecimal quantity;

	@Column(name = "rate", precision = 19, scale = 4)
	private BigDecimal rate;

	@Column(name = "amount", nullable = false, precision = 19, scale = 4)
	private BigDecimal amount;

	@Column(name = "manual_override", nullable = false)
	private boolean manualOverride;

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

	public UUID getPayPeriodId() {
		return payPeriodId;
	}

	public void setPayPeriodId(UUID payPeriodId) {
		this.payPeriodId = payPeriodId;
	}

	public UUID getPayPeriodRunId() {
		return payPeriodRunId;
	}

	public void setPayPeriodRunId(UUID payPeriodRunId) {
		this.payPeriodRunId = payPeriodRunId;
	}

	public UUID getTenantWageComponentId() {
		return tenantWageComponentId;
	}

	public void setTenantWageComponentId(UUID tenantWageComponentId) {
		this.tenantWageComponentId = tenantWageComponentId;
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

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}

	public boolean isManualOverride() {
		return manualOverride;
	}

	public void setManualOverride(boolean manualOverride) {
		this.manualOverride = manualOverride;
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
