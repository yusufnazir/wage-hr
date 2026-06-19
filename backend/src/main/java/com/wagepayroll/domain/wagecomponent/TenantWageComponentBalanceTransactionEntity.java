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
import com.wagepayroll.payroll.model.BalanceTransactionKind;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "tenant_wage_component_balance_transaction")
public class TenantWageComponentBalanceTransactionEntity extends TenantScopedEntity {

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "balance_id", length = 36, nullable = false)
	private UUID balanceId;

	@Column(name = "change_amount", nullable = false, precision = 19, scale = 4)
	private BigDecimal changeAmount;

	@Column(name = "balance_after", nullable = false, precision = 19, scale = 4)
	private BigDecimal balanceAfter;

	@Enumerated(EnumType.STRING)
	@Column(name = "transaction_kind", nullable = false, length = 30)
	private BalanceTransactionKind transactionKind;

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "pay_period_run_id", length = 36)
	private UUID payPeriodRunId;

	@Column(name = "remarks", length = 500)
	private String remarks;

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	public UUID getBalanceId() {
		return balanceId;
	}

	public void setBalanceId(UUID balanceId) {
		this.balanceId = balanceId;
	}

	public BigDecimal getChangeAmount() {
		return changeAmount;
	}

	public void setChangeAmount(BigDecimal changeAmount) {
		this.changeAmount = changeAmount;
	}

	public BigDecimal getBalanceAfter() {
		return balanceAfter;
	}

	public void setBalanceAfter(BigDecimal balanceAfter) {
		this.balanceAfter = balanceAfter;
	}

	public BalanceTransactionKind getTransactionKind() {
		return transactionKind;
	}

	public void setTransactionKind(BalanceTransactionKind transactionKind) {
		this.transactionKind = transactionKind;
	}

	public UUID getPayPeriodRunId() {
		return payPeriodRunId;
	}

	public void setPayPeriodRunId(UUID payPeriodRunId) {
		this.payPeriodRunId = payPeriodRunId;
	}

	public String getRemarks() {
		return remarks;
	}

	public void setRemarks(String remarks) {
		this.remarks = remarks;
	}

	public Instant getOccurredAt() {
		return occurredAt;
	}

	public void setOccurredAt(Instant occurredAt) {
		this.occurredAt = occurredAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}
}
