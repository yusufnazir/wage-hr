package com.wagepayroll.liquibase.task;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Upserts one {@code tenant_company} row.
 */
public class DataUpsertTenantCompany extends CustomDataTaskChange {

	private String id;
	private String tenantId;
	private String name;
	private String legalName;
	private String registrationNumber;
	private String taxId;
	private String payrollCountry;
	private String currency;
	private String payrollFrequency;
	private String timezone;
	private String dateFormat;
	private String contactEmail;
	private String contactPhone;
	private String addressLine1;
	private String addressLine2;
	private String city;
	private String stateRegion;
	private String postalCode;
	private String country;
	private String active;
	private String payPeriodEndDate;
	private String timesheetEndDate;
	private String currentYear;
	private String currentPeriod;

	@Override
	public void handleUpdate() throws Exception {
		boolean activeBool = active == null || active.isBlank() || Boolean.parseBoolean(active.trim());
		Integer year = currentYear == null || currentYear.isBlank() ? null : Integer.parseInt(currentYear.trim());
		Integer period = currentPeriod == null || currentPeriod.isBlank() ? null : Integer.parseInt(currentPeriod.trim());

		try (PreparedStatement check = connection.prepareStatement("SELECT COUNT(*) FROM tenant_company WHERE id = ?")) {
			setData(check, 1, id);
			try (ResultSet rs = check.executeQuery()) {
				rs.next();
				if (rs.getInt(1) > 0) {
					try (PreparedStatement ps = connection.prepareStatement("""
							UPDATE tenant_company SET
							  tenant_id = ?, name = ?, legal_name = ?, registration_number = ?, tax_id = ?,
							  payroll_country = ?, currency = ?, payroll_frequency = ?, timezone = ?, date_format = ?,
							  contact_email = ?, contact_phone = ?, address_line1 = ?, address_line2 = ?,
							  city = ?, state_region = ?, postal_code = ?, country = ?, active = ?,
							  pay_period_end_date = ?, timesheet_end_date = ?, current_year = ?, current_period = ?,
							  updated_at = ?
							WHERE id = ?
							""")) {
						int i = 1;
						setData(ps, i++, tenantId);
						setData(ps, i++, name);
						setData(ps, i++, legalName);
						setData(ps, i++, registrationNumber);
						setData(ps, i++, taxId);
						setData(ps, i++, payrollCountry);
						setData(ps, i++, currency);
						setData(ps, i++, payrollFrequency);
						setData(ps, i++, timezone);
						setData(ps, i++, dateFormat);
						setData(ps, i++, contactEmail);
						setData(ps, i++, contactPhone);
						setData(ps, i++, addressLine1);
						setData(ps, i++, addressLine2);
						setData(ps, i++, city);
						setData(ps, i++, stateRegion);
						setData(ps, i++, postalCode);
						setData(ps, i++, country);
						ps.setBoolean(i++, activeBool);
						setDate(ps, i++, payPeriodEndDate);
						setDate(ps, i++, timesheetEndDate);
						if (year == null) ps.setNull(i++, java.sql.Types.INTEGER);
						else ps.setInt(i++, year);
						if (period == null) ps.setNull(i++, java.sql.Types.INTEGER);
						else ps.setInt(i++, period);
						setData(ps, i++, ts);
						setData(ps, i++, id);
						ps.executeUpdate();
					}
					return;
				}
			}
		}
		try (PreparedStatement ps = connection.prepareStatement("""
				INSERT INTO tenant_company (
				  id, tenant_id, name, legal_name, registration_number, tax_id,
				  payroll_country, currency, payroll_frequency, timezone, date_format,
				  contact_email, contact_phone, address_line1, address_line2,
				  city, state_region, postal_code, country, active,
				  pay_period_end_date, timesheet_end_date, current_year, current_period,
				  created_at, updated_at
				) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
				""")) {
			int i = 1;
			setData(ps, i++, id);
			setData(ps, i++, tenantId);
			setData(ps, i++, name);
			setData(ps, i++, legalName);
			setData(ps, i++, registrationNumber);
			setData(ps, i++, taxId);
			setData(ps, i++, payrollCountry);
			setData(ps, i++, currency);
			setData(ps, i++, payrollFrequency);
			setData(ps, i++, timezone);
			setData(ps, i++, dateFormat);
			setData(ps, i++, contactEmail);
			setData(ps, i++, contactPhone);
			setData(ps, i++, addressLine1);
			setData(ps, i++, addressLine2);
			setData(ps, i++, city);
			setData(ps, i++, stateRegion);
			setData(ps, i++, postalCode);
			setData(ps, i++, country);
			ps.setBoolean(i++, activeBool);
			setDate(ps, i++, payPeriodEndDate);
			setDate(ps, i++, timesheetEndDate);
			if (year == null) ps.setNull(i++, java.sql.Types.INTEGER);
			else ps.setInt(i++, year);
			if (period == null) ps.setNull(i++, java.sql.Types.INTEGER);
			else ps.setInt(i++, period);
			setData(ps, i++, ts);
			setData(ps, i++, ts);
			ps.executeUpdate();
		}
	}

	public String getId() { return id; }
	public void setId(String id) { this.id = id; }
	public String getTenantId() { return tenantId; }
	public void setTenantId(String tenantId) { this.tenantId = tenantId; }
	public String getName() { return name; }
	public void setName(String name) { this.name = name; }
	public String getLegalName() { return legalName; }
	public void setLegalName(String legalName) { this.legalName = legalName; }
	public String getRegistrationNumber() { return registrationNumber; }
	public void setRegistrationNumber(String registrationNumber) { this.registrationNumber = registrationNumber; }
	public String getTaxId() { return taxId; }
	public void setTaxId(String taxId) { this.taxId = taxId; }
	public String getPayrollCountry() { return payrollCountry; }
	public void setPayrollCountry(String payrollCountry) { this.payrollCountry = payrollCountry; }
	public String getCurrency() { return currency; }
	public void setCurrency(String currency) { this.currency = currency; }
	public String getPayrollFrequency() { return payrollFrequency; }
	public void setPayrollFrequency(String payrollFrequency) { this.payrollFrequency = payrollFrequency; }
	public String getTimezone() { return timezone; }
	public void setTimezone(String timezone) { this.timezone = timezone; }
	public String getDateFormat() { return dateFormat; }
	public void setDateFormat(String dateFormat) { this.dateFormat = dateFormat; }
	public String getContactEmail() { return contactEmail; }
	public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }
	public String getContactPhone() { return contactPhone; }
	public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
	public String getAddressLine1() { return addressLine1; }
	public void setAddressLine1(String addressLine1) { this.addressLine1 = addressLine1; }
	public String getAddressLine2() { return addressLine2; }
	public void setAddressLine2(String addressLine2) { this.addressLine2 = addressLine2; }
	public String getCity() { return city; }
	public void setCity(String city) { this.city = city; }
	public String getStateRegion() { return stateRegion; }
	public void setStateRegion(String stateRegion) { this.stateRegion = stateRegion; }
	public String getPostalCode() { return postalCode; }
	public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
	public String getCountry() { return country; }
	public void setCountry(String country) { this.country = country; }
	public String getActive() { return active; }
	public void setActive(String active) { this.active = active; }
	public String getPayPeriodEndDate() { return payPeriodEndDate; }
	public void setPayPeriodEndDate(String payPeriodEndDate) { this.payPeriodEndDate = payPeriodEndDate; }
	public String getTimesheetEndDate() { return timesheetEndDate; }
	public void setTimesheetEndDate(String timesheetEndDate) { this.timesheetEndDate = timesheetEndDate; }
	public String getCurrentYear() { return currentYear; }
	public void setCurrentYear(String currentYear) { this.currentYear = currentYear; }
	public String getCurrentPeriod() { return currentPeriod; }
	public void setCurrentPeriod(String currentPeriod) { this.currentPeriod = currentPeriod; }
}
