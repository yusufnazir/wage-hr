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
@Table(name = "tenant_wage_component_balance")
public class TenantWageComponentBalanceEntity extends TenantScopedEntity {

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "company_id", length = 36, nullable = false)
	private UUID companyId;

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "employee_id", length = 36, nullable = false)
	private UUID employeeId;

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "tenant_wage_component_id", length = 36, nullable = false)
	private UUID tenantWageComponentId;

	@Column(name = "currency_code", length = 3, columnDefinition = "CHAR(3)")
	private String currencyCode;

	@Column(name = "current_balance", nullable = false, precision = 19, scale = 4)
	private BigDecimal currentBalance;

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

	public String getCurrencyCode() {
		return currencyCode;
	}

	public void setCurrencyCode(String currencyCode) {
		this.currencyCode = currencyCode;
	}

	public BigDecimal getCurrentBalance() {
		return currentBalance;
	}

	public void setCurrentBalance(BigDecimal currentBalance) {
		this.currentBalance = currentBalance;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}
}
