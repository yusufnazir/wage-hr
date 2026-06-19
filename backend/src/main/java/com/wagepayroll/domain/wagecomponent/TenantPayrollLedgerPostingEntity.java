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
@Table(name = "tenant_payroll_ledger_posting")
public class TenantPayrollLedgerPostingEntity extends TenantScopedEntity {

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "pay_period_run_id", length = 36, nullable = false)
	private UUID payPeriodRunId;

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "employee_id", length = 36, nullable = false)
	private UUID employeeId;

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "tenant_payroll_result_line_id", length = 36)
	private UUID tenantPayrollResultLineId;

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "debit_tenant_ledger_id", length = 36, nullable = false)
	private UUID debitTenantLedgerId;

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "credit_tenant_ledger_id", length = 36, nullable = false)
	private UUID creditTenantLedgerId;

	@Column(name = "amount", nullable = false, precision = 19, scale = 4)
	private BigDecimal amount;

	@Column(name = "currency_code", nullable = false, length = 3, columnDefinition = "CHAR(3)")
	private String currencyCode;

	@Column(name = "posting_sequence", nullable = false)
	private int postingSequence;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	public UUID getPayPeriodRunId() {
		return payPeriodRunId;
	}

	public void setPayPeriodRunId(UUID payPeriodRunId) {
		this.payPeriodRunId = payPeriodRunId;
	}

	public UUID getEmployeeId() {
		return employeeId;
	}

	public void setEmployeeId(UUID employeeId) {
		this.employeeId = employeeId;
	}

	public UUID getTenantPayrollResultLineId() {
		return tenantPayrollResultLineId;
	}

	public void setTenantPayrollResultLineId(UUID tenantPayrollResultLineId) {
		this.tenantPayrollResultLineId = tenantPayrollResultLineId;
	}

	public UUID getDebitTenantLedgerId() {
		return debitTenantLedgerId;
	}

	public void setDebitTenantLedgerId(UUID debitTenantLedgerId) {
		this.debitTenantLedgerId = debitTenantLedgerId;
	}

	public UUID getCreditTenantLedgerId() {
		return creditTenantLedgerId;
	}

	public void setCreditTenantLedgerId(UUID creditTenantLedgerId) {
		this.creditTenantLedgerId = creditTenantLedgerId;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}

	public String getCurrencyCode() {
		return currencyCode;
	}

	public void setCurrencyCode(String currencyCode) {
		this.currencyCode = currencyCode;
	}

	public int getPostingSequence() {
		return postingSequence;
	}

	public void setPostingSequence(int postingSequence) {
		this.postingSequence = postingSequence;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}
}
