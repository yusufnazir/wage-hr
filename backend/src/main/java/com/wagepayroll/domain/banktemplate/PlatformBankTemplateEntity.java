package com.wagepayroll.domain.banktemplate;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.wagepayroll.domain.AbstractUuidEntity;

@Entity
@Table(name = "platform_bank_template")
public class PlatformBankTemplateEntity extends AbstractUuidEntity {

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

	@Column(name = "active", nullable = false)
	private boolean active;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

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
