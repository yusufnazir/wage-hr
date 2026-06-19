package com.wagepayroll.domain.employeepayment;

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
@Table(name = "tenant_employee_pay_period_payment")
public class TenantEmployeePayPeriodPaymentEntity extends TenantScopedEntity {

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "company_id", nullable = false, length = 36)
	private UUID companyId;

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "employee_id", nullable = false, length = 36)
	private UUID employeeId;

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "pay_period_id", nullable = false, length = 36)
	private UUID payPeriodId;

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "pay_period_run_id", nullable = false, length = 36)
	private UUID payPeriodRunId;

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "destination_id", length = 36)
	private UUID destinationId;

	@Column(name = "channel_type", nullable = false, length = 20)
	private String channelType;

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "payment_location_id", length = 36)
	private UUID paymentLocationId;

	@Column(name = "payment_location_name", length = 120)
	private String paymentLocationName;

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "bank_template_id", length = 36)
	private UUID bankTemplateId;

	@Column(name = "bank_name", length = 180)
	private String bankName;

	@Column(name = "account_number", length = 60)
	private String accountNumber;

	@Column(name = "currency", nullable = false, length = 3, columnDefinition = "CHAR(3)")
	private String currency;

	@Column(name = "split_type", nullable = false, length = 20)
	private String splitType;

	@Column(name = "split_value", nullable = false, precision = 19, scale = 4)
	private BigDecimal splitValue;

	@Column(name = "allocated_amount", nullable = false, precision = 19, scale = 4)
	private BigDecimal allocatedAmount;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

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

	public UUID getDestinationId() {
		return destinationId;
	}

	public void setDestinationId(UUID destinationId) {
		this.destinationId = destinationId;
	}

	public String getChannelType() {
		return channelType;
	}

	public void setChannelType(String channelType) {
		this.channelType = channelType;
	}

	public UUID getPaymentLocationId() {
		return paymentLocationId;
	}

	public void setPaymentLocationId(UUID paymentLocationId) {
		this.paymentLocationId = paymentLocationId;
	}

	public String getPaymentLocationName() {
		return paymentLocationName;
	}

	public void setPaymentLocationName(String paymentLocationName) {
		this.paymentLocationName = paymentLocationName;
	}

	public UUID getBankTemplateId() {
		return bankTemplateId;
	}

	public void setBankTemplateId(UUID bankTemplateId) {
		this.bankTemplateId = bankTemplateId;
	}

	public String getBankName() {
		return bankName;
	}

	public void setBankName(String bankName) {
		this.bankName = bankName;
	}

	public String getAccountNumber() {
		return accountNumber;
	}

	public void setAccountNumber(String accountNumber) {
		this.accountNumber = accountNumber;
	}

	public String getCurrency() {
		return currency;
	}

	public void setCurrency(String currency) {
		this.currency = currency;
	}

	public String getSplitType() {
		return splitType;
	}

	public void setSplitType(String splitType) {
		this.splitType = splitType;
	}

	public BigDecimal getSplitValue() {
		return splitValue;
	}

	public void setSplitValue(BigDecimal splitValue) {
		this.splitValue = splitValue;
	}

	public BigDecimal getAllocatedAmount() {
		return allocatedAmount;
	}

	public void setAllocatedAmount(BigDecimal allocatedAmount) {
		this.allocatedAmount = allocatedAmount;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}
}
