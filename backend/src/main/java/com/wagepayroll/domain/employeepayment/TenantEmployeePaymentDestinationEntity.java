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
@Table(name = "tenant_employee_payment_destination")
public class TenantEmployeePaymentDestinationEntity extends TenantScopedEntity {

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "company_id", nullable = false, length = 36)
	private UUID companyId;

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "employee_id", nullable = false, length = 36)
	private UUID employeeId;

	@Column(name = "channel_type", nullable = false, length = 20)
	private String channelType;

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "payment_location_id", length = 36)
	private UUID paymentLocationId;

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "bank_template_id", length = 36)
	private UUID bankTemplateId;

	@Column(name = "account_number", length = 60)
	private String accountNumber;

	@Column(name = "currency", nullable = false, length = 3, columnDefinition = "CHAR(3)")
	private String currency;

	@Column(name = "split_type", nullable = false, length = 20)
	private String splitType;

	@Column(name = "split_value", nullable = false, precision = 19, scale = 4)
	private BigDecimal splitValue;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	@Column(name = "active", nullable = false)
	private boolean active;

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

	public UUID getBankTemplateId() {
		return bankTemplateId;
	}

	public void setBankTemplateId(UUID bankTemplateId) {
		this.bankTemplateId = bankTemplateId;
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

	public int getSortOrder() {
		return sortOrder;
	}

	public void setSortOrder(int sortOrder) {
		this.sortOrder = sortOrder;
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
