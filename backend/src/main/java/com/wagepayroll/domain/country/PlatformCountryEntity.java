package com.wagepayroll.domain.country;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.wagepayroll.domain.AbstractUuidEntity;

@Entity
@Table(name = "platform_country")
public class PlatformCountryEntity extends AbstractUuidEntity {

	@Column(name = "iso_alpha2", nullable = false, unique = true, length = 2)
	private String isoAlpha2;

	@Column(name = "iso_alpha3", nullable = false, unique = true, length = 3)
	private String isoAlpha3;

	@Column(name = "iso_numeric", nullable = false, length = 3)
	private String isoNumeric;

	@Column(name = "dial_code", length = 15)
	private String dialCode;

	@Column(name = "active", nullable = false)
	private boolean active;

	@Column(name = "payroll_enabled", nullable = false)
	private boolean payrollEnabled;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public String getIsoAlpha2() {
		return isoAlpha2;
	}

	public void setIsoAlpha2(String isoAlpha2) {
		this.isoAlpha2 = isoAlpha2;
	}

	public String getIsoAlpha3() {
		return isoAlpha3;
	}

	public void setIsoAlpha3(String isoAlpha3) {
		this.isoAlpha3 = isoAlpha3;
	}

	public String getIsoNumeric() {
		return isoNumeric;
	}

	public void setIsoNumeric(String isoNumeric) {
		this.isoNumeric = isoNumeric;
	}

	public String getDialCode() {
		return dialCode;
	}

	public void setDialCode(String dialCode) {
		this.dialCode = dialCode;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public boolean isPayrollEnabled() {
		return payrollEnabled;
	}

	public void setPayrollEnabled(boolean payrollEnabled) {
		this.payrollEnabled = payrollEnabled;
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
