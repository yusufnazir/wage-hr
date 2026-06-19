package com.wagepayroll.domain.org;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.wagepayroll.domain.TenantScopedEntity;

@Entity
@Table(name = "tenant_company")
public class TenantCompanyEntity extends TenantScopedEntity {

	@Column(name = "name", nullable = false, length = 120)
	private String name;

	@Column(name = "legal_name", nullable = false, length = 180)
	private String legalName;

	@Column(name = "registration_number", length = 80)
	private String registrationNumber;

	@Column(name = "tax_id", nullable = false, length = 80)
	private String taxId;

	@Column(name = "payroll_country", nullable = false, length = 2, columnDefinition = "CHAR(2)")
	private String payrollCountry;

	@Column(name = "currency", nullable = false, length = 3, columnDefinition = "CHAR(3)")
	private String currency;

	@Column(name = "payroll_frequency", nullable = false, length = 20)
	private String payrollFrequency;

	@Column(name = "timezone", nullable = false, length = 60)
	private String timezone;

	@Column(name = "date_format", nullable = false, length = 20)
	private String dateFormat;

	@Column(name = "contact_email", length = 190)
	private String contactEmail;

	@Column(name = "contact_phone", length = 40)
	private String contactPhone;

	@Column(name = "address_line1", length = 180)
	private String addressLine1;

	@Column(name = "address_line2", length = 180)
	private String addressLine2;

	@Column(name = "city", length = 120)
	private String city;

	@Column(name = "state_region", length = 120)
	private String stateRegion;

	@Column(name = "postal_code", length = 30)
	private String postalCode;

	@Column(name = "country", length = 2, columnDefinition = "CHAR(2)")
	private String country;

	@Column(name = "pay_period_end_date")
	private LocalDate payPeriodEndDate;

	@Column(name = "timesheet_end_date")
	private LocalDate timesheetEndDate;

	@Column(name = "current_year")
	private Integer currentYear;

	@Column(name = "current_period")
	private Integer currentPeriod;

	@Column(name = "logo_storage_key", length = 512)
	private String logoStorageKey;

	@Column(name = "active", nullable = false)
	private boolean active;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getLegalName() {
		return legalName;
	}

	public void setLegalName(String legalName) {
		this.legalName = legalName;
	}

	public String getRegistrationNumber() {
		return registrationNumber;
	}

	public void setRegistrationNumber(String registrationNumber) {
		this.registrationNumber = registrationNumber;
	}

	public String getTaxId() {
		return taxId;
	}

	public void setTaxId(String taxId) {
		this.taxId = taxId;
	}

	public String getPayrollCountry() {
		return payrollCountry;
	}

	public void setPayrollCountry(String payrollCountry) {
		this.payrollCountry = payrollCountry;
	}

	public String getCurrency() {
		return currency;
	}

	public void setCurrency(String currency) {
		this.currency = currency;
	}

	public String getPayrollFrequency() {
		return payrollFrequency;
	}

	public void setPayrollFrequency(String payrollFrequency) {
		this.payrollFrequency = payrollFrequency;
	}

	public String getTimezone() {
		return timezone;
	}

	public void setTimezone(String timezone) {
		this.timezone = timezone;
	}

	public String getDateFormat() {
		return dateFormat;
	}

	public void setDateFormat(String dateFormat) {
		this.dateFormat = dateFormat;
	}

	public String getContactEmail() {
		return contactEmail;
	}

	public void setContactEmail(String contactEmail) {
		this.contactEmail = contactEmail;
	}

	public String getContactPhone() {
		return contactPhone;
	}

	public void setContactPhone(String contactPhone) {
		this.contactPhone = contactPhone;
	}

	public String getAddressLine1() {
		return addressLine1;
	}

	public void setAddressLine1(String addressLine1) {
		this.addressLine1 = addressLine1;
	}

	public String getAddressLine2() {
		return addressLine2;
	}

	public void setAddressLine2(String addressLine2) {
		this.addressLine2 = addressLine2;
	}

	public String getCity() {
		return city;
	}

	public void setCity(String city) {
		this.city = city;
	}

	public String getStateRegion() {
		return stateRegion;
	}

	public void setStateRegion(String stateRegion) {
		this.stateRegion = stateRegion;
	}

	public String getPostalCode() {
		return postalCode;
	}

	public void setPostalCode(String postalCode) {
		this.postalCode = postalCode;
	}

	public String getCountry() {
		return country;
	}

	public void setCountry(String country) {
		this.country = country;
	}

	public LocalDate getPayPeriodEndDate() {
		return payPeriodEndDate;
	}

	public void setPayPeriodEndDate(LocalDate payPeriodEndDate) {
		this.payPeriodEndDate = payPeriodEndDate;
	}

	public LocalDate getTimesheetEndDate() {
		return timesheetEndDate;
	}

	public void setTimesheetEndDate(LocalDate timesheetEndDate) {
		this.timesheetEndDate = timesheetEndDate;
	}

	public Integer getCurrentYear() {
		return currentYear;
	}

	public void setCurrentYear(Integer currentYear) {
		this.currentYear = currentYear;
	}

	public Integer getCurrentPeriod() {
		return currentPeriod;
	}

	public void setCurrentPeriod(Integer currentPeriod) {
		this.currentPeriod = currentPeriod;
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

	public String getLogoStorageKey() {
		return logoStorageKey;
	}

	public void setLogoStorageKey(String logoStorageKey) {
		this.logoStorageKey = logoStorageKey;
	}
}
