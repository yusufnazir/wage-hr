package com.wagepayroll.domain.wagecomponent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.wagepayroll.domain.TenantScopedEntity;
import com.wagepayroll.payroll.model.PayrollComponentSource;
import com.wagepayroll.payroll.model.PayrollPhase;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "tenant_payroll_result_line")
public class TenantPayrollResultLineEntity extends TenantScopedEntity {

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "company_id", length = 36, nullable = false)
	private UUID companyId;

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "pay_period_run_id", length = 36, nullable = false)
	private UUID payPeriodRunId;

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "employee_id", length = 36, nullable = false)
	private UUID employeeId;

	@Enumerated(EnumType.STRING)
	@Column(name = "component_source", nullable = false, length = 10)
	private PayrollComponentSource componentSource;

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "component_ref_id", length = 36, nullable = false)
	private UUID componentRefId;

	@Enumerated(EnumType.STRING)
	@Column(name = "phase", nullable = false, length = 20)
	private PayrollPhase phase;

	@Column(name = "processing_order_snapshot", nullable = false)
	private int processingOrderSnapshot;

	@Column(name = "quantity", precision = 19, scale = 4)
	private BigDecimal quantity;

	@Column(name = "rate", precision = 19, scale = 4)
	private BigDecimal rate;

	@Column(name = "amount", nullable = false, precision = 19, scale = 4)
	private BigDecimal amount;

	@Column(name = "rounded_amount", nullable = false, precision = 19, scale = 4)
	private BigDecimal roundedAmount;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	public UUID getCompanyId() {
		return companyId;
	}

	public void setCompanyId(UUID companyId) {
		this.companyId = companyId;
	}

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

	public PayrollComponentSource getComponentSource() {
		return componentSource;
	}

	public void setComponentSource(PayrollComponentSource componentSource) {
		this.componentSource = componentSource;
	}

	public UUID getComponentRefId() {
		return componentRefId;
	}

	public void setComponentRefId(UUID componentRefId) {
		this.componentRefId = componentRefId;
	}

	public PayrollPhase getPhase() {
		return phase;
	}

	public void setPhase(PayrollPhase phase) {
		this.phase = phase;
	}

	public int getProcessingOrderSnapshot() {
		return processingOrderSnapshot;
	}

	public void setProcessingOrderSnapshot(int processingOrderSnapshot) {
		this.processingOrderSnapshot = processingOrderSnapshot;
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

	public BigDecimal getRoundedAmount() {
		return roundedAmount;
	}

	public void setRoundedAmount(BigDecimal roundedAmount) {
		this.roundedAmount = roundedAmount;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}
}
