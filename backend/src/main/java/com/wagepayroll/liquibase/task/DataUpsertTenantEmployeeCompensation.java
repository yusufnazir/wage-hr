package com.wagepayroll.liquibase.task;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

/** Upserts {@code tenant_employee_compensation} keyed by {@code employee_id}. */
public class DataUpsertTenantEmployeeCompensation extends CustomDataTaskChange {

	private String id;
	private String tenantId;
	private String companyId;
	private String employeeId;
	private String currencyCode;
	private String wageType;
	private String wageAmount;
	private String workTimeId;
	private String applyTaxes;
	private String applyTaxExempt;
	private String applyAov;
	private String notes;

	@Override
	public void handleUpdate() throws Exception {
		boolean taxesBool = applyTaxes == null || applyTaxes.isBlank() || Boolean.parseBoolean(applyTaxes.trim());
		boolean exemptBool = applyTaxExempt == null || applyTaxExempt.isBlank()
				|| Boolean.parseBoolean(applyTaxExempt.trim());
		boolean aovBool = applyAov == null || applyAov.isBlank() || Boolean.parseBoolean(applyAov.trim());
		try (PreparedStatement check = connection
				.prepareStatement("SELECT id FROM tenant_employee_compensation WHERE employee_id = ?")) {
			setData(check, 1, employeeId);
			try (ResultSet rs = check.executeQuery()) {
				if (rs.next()) {
					String existingId = rs.getString(1);
					try (PreparedStatement ps = connection.prepareStatement("""
							UPDATE tenant_employee_compensation SET
							  tenant_id = ?, company_id = ?, currency_code = ?, wage_type = ?, wage_amount = ?,
							  work_time_id = ?, apply_taxes = ?, apply_tax_exempt = ?, apply_aov = ?, notes = ?,
							  updated_at = ?
							WHERE id = ?
							""")) {
						int i = 1;
						setData(ps, i++, tenantId);
						setData(ps, i++, companyId);
						setData(ps, i++, currencyCode);
						setData(ps, i++, wageType);
						setDecimal(ps, i++, wageAmount);
						setData(ps, i++, workTimeId);
						ps.setBoolean(i++, taxesBool);
						ps.setBoolean(i++, exemptBool);
						ps.setBoolean(i++, aovBool);
						setData(ps, i++, notes);
						setData(ps, i++, ts);
						setData(ps, i++, existingId);
						ps.executeUpdate();
					}
					return;
				}
			}
		}
		try (PreparedStatement ps = connection.prepareStatement("""
				INSERT INTO tenant_employee_compensation (
				  id, tenant_id, company_id, employee_id, currency_code, wage_type, wage_amount, work_time_id,
				  apply_taxes, apply_tax_exempt, apply_aov, notes, created_at, updated_at
				) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
				""")) {
			int i = 1;
			setData(ps, i++, id);
			setData(ps, i++, tenantId);
			setData(ps, i++, companyId);
			setData(ps, i++, employeeId);
			setData(ps, i++, currencyCode);
			setData(ps, i++, wageType);
			setDecimal(ps, i++, wageAmount);
			setData(ps, i++, workTimeId);
			ps.setBoolean(i++, taxesBool);
			ps.setBoolean(i++, exemptBool);
			ps.setBoolean(i++, aovBool);
			setData(ps, i++, notes);
			setData(ps, i++, ts);
			setData(ps, i++, ts);
			ps.executeUpdate();
		}
	}

	public String getId() { return id; }
	public void setId(String id) { this.id = id; }
	public String getTenantId() { return tenantId; }
	public void setTenantId(String tenantId) { this.tenantId = tenantId; }
	public String getCompanyId() { return companyId; }
	public void setCompanyId(String companyId) { this.companyId = companyId; }
	public String getEmployeeId() { return employeeId; }
	public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
	public String getCurrencyCode() { return currencyCode; }
	public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
	public String getWageType() { return wageType; }
	public void setWageType(String wageType) { this.wageType = wageType; }
	public String getWageAmount() { return wageAmount; }
	public void setWageAmount(String wageAmount) { this.wageAmount = wageAmount; }
	public String getWorkTimeId() { return workTimeId; }
	public void setWorkTimeId(String workTimeId) { this.workTimeId = workTimeId; }
	public String getApplyTaxes() { return applyTaxes; }
	public void setApplyTaxes(String applyTaxes) { this.applyTaxes = applyTaxes; }
	public String getApplyTaxExempt() { return applyTaxExempt; }
	public void setApplyTaxExempt(String applyTaxExempt) { this.applyTaxExempt = applyTaxExempt; }
	public String getApplyAov() { return applyAov; }
	public void setApplyAov(String applyAov) { this.applyAov = applyAov; }
	public String getNotes() { return notes; }
	public void setNotes(String notes) { this.notes = notes; }
}
