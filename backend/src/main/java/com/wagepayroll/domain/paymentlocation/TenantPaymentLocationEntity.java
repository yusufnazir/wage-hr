package com.wagepayroll.domain.paymentlocation;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.wagepayroll.domain.TenantScopedEntity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "tenant_payment_location")
public class TenantPaymentLocationEntity extends TenantScopedEntity {

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "company_id", nullable = false, length = 36)
	private UUID companyId;

	@Column(name = "name", nullable = false, length = 120)
	private String name;

	@Column(name = "payment_type", nullable = false, length = 20)
	private String paymentType;

	@Column(name = "currency", nullable = false, length = 3, columnDefinition = "CHAR(3)")
	private String currency;

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "bank_template_id", length = 36)
	private UUID bankTemplateId;

	@Column(name = "account_number", length = 60)
	private String accountNumber;

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

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getPaymentType() {
		return paymentType;
	}

	public void setPaymentType(String paymentType) {
		this.paymentType = paymentType;
	}

	public String getCurrency() {
		return currency;
	}

	public void setCurrency(String currency) {
		this.currency = currency;
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
