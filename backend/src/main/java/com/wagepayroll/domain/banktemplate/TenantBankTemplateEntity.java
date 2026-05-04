package com.wagepayroll.domain.banktemplate;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.wagepayroll.domain.TenantScopedEntity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "tenant_bank_template")
public class TenantBankTemplateEntity extends TenantScopedEntity {

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "company_id", nullable = false, length = 36)
	private UUID companyId;

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "platform_bank_template_id", length = 36)
	private UUID platformBankTemplateId;

	@Column(name = "country_code", nullable = false, length = 2, columnDefinition = "CHAR(2)")
	private String countryCode;

	@Column(name = "name", nullable = false, length = 150)
	private String name;

	@Column(name = "bank_name", length = 150)
	private String bankName;

	@Column(name = "swift_bic", length = 11)
	private String swiftBic;

	@Column(name = "bank_code", length = 30)
	private String bankCode;

	@Column(name = "account_number_format", length = 100)
	private String accountNumberFormat;

	@Column(name = "currency_code", length = 3, columnDefinition = "CHAR(3)")
	private String currencyCode;

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

	public UUID getPlatformBankTemplateId() {
		return platformBankTemplateId;
	}

	public void setPlatformBankTemplateId(UUID platformBankTemplateId) {
		this.platformBankTemplateId = platformBankTemplateId;
	}

	public String getCountryCode() {
		return countryCode;
	}

	public void setCountryCode(String countryCode) {
		this.countryCode = countryCode;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getBankName() {
		return bankName;
	}

	public void setBankName(String bankName) {
		this.bankName = bankName;
	}

	public String getSwiftBic() {
		return swiftBic;
	}

	public void setSwiftBic(String swiftBic) {
		this.swiftBic = swiftBic;
	}

	public String getBankCode() {
		return bankCode;
	}

	public void setBankCode(String bankCode) {
		this.bankCode = bankCode;
	}

	public String getAccountNumberFormat() {
		return accountNumberFormat;
	}

	public void setAccountNumberFormat(String accountNumberFormat) {
		this.accountNumberFormat = accountNumberFormat;
	}

	public String getCurrencyCode() {
		return currencyCode;
	}

	public void setCurrencyCode(String currencyCode) {
		this.currencyCode = currencyCode;
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
