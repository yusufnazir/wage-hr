package com.wagepayroll.liquibase.task;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

/** Upserts {@code tenant_employee_payroll_standing_instruction}. */
public class DataUpsertTenantEmployeePayrollStandingInstruction extends CustomDataTaskChange {

	private String id;
	private String tenantId;
	private String companyId;
	private String employeeId;
	private String tenantWageComponentId;
	private String effectiveFrom;
	private String effectiveTo;
	private String amount;
	private String quantity;
	private String rate;
	private String recurrence;
	private String active;
	private String remarks;

	@Override
	public void handleUpdate() throws Exception {
		boolean activeBool = active == null || active.isBlank() || Boolean.parseBoolean(active.trim());
		try (PreparedStatement check = connection.prepareStatement(
				"SELECT COUNT(*) FROM tenant_employee_payroll_standing_instruction WHERE id = ?")) {
			setData(check, 1, id);
			try (ResultSet rs = check.executeQuery()) {
				rs.next();
				if (rs.getInt(1) > 0) {
					try (PreparedStatement ps = connection.prepareStatement("""
							UPDATE tenant_employee_payroll_standing_instruction SET
							  tenant_id = ?, company_id = ?, employee_id = ?, tenant_wage_component_id = ?,
							  effective_from = ?, effective_to = ?, amount = ?, quantity = ?, rate = ?,
							  recurrence = ?, active = ?, remarks = ?, updated_at = ?
							WHERE id = ?
							""")) {
						int i = 1;
						setData(ps, i++, tenantId);
						setData(ps, i++, companyId);
						setData(ps, i++, employeeId);
						setData(ps, i++, tenantWageComponentId);
						setDate(ps, i++, effectiveFrom);
						setDate(ps, i++, effectiveTo);
						setDecimal(ps, i++, amount);
						setDecimal(ps, i++, quantity);
						setDecimal(ps, i++, rate);
						setData(ps, i++, recurrence);
						ps.setBoolean(i++, activeBool);
						setData(ps, i++, remarks);
						setData(ps, i++, ts);
						setData(ps, i++, id);
						ps.executeUpdate();
					}
					return;
				}
			}
		}
		try (PreparedStatement ps = connection.prepareStatement("""
				INSERT INTO tenant_employee_payroll_standing_instruction (
				  id, tenant_id, company_id, employee_id, tenant_wage_component_id,
				  effective_from, effective_to, amount, quantity, rate, recurrence, active, remarks, created_at, updated_at
				) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
				""")) {
			int i = 1;
			setData(ps, i++, id);
			setData(ps, i++, tenantId);
			setData(ps, i++, companyId);
			setData(ps, i++, employeeId);
			setData(ps, i++, tenantWageComponentId);
			setDate(ps, i++, effectiveFrom);
			setDate(ps, i++, effectiveTo);
			setDecimal(ps, i++, amount);
			setDecimal(ps, i++, quantity);
			setDecimal(ps, i++, rate);
			setData(ps, i++, recurrence);
			ps.setBoolean(i++, activeBool);
			setData(ps, i++, remarks);
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
	public String getTenantWageComponentId() { return tenantWageComponentId; }
	public void setTenantWageComponentId(String tenantWageComponentId) { this.tenantWageComponentId = tenantWageComponentId; }
	public String getEffectiveFrom() { return effectiveFrom; }
	public void setEffectiveFrom(String effectiveFrom) { this.effectiveFrom = effectiveFrom; }
	public String getEffectiveTo() { return effectiveTo; }
	public void setEffectiveTo(String effectiveTo) { this.effectiveTo = effectiveTo; }
	public String getAmount() { return amount; }
	public void setAmount(String amount) { this.amount = amount; }
	public String getQuantity() { return quantity; }
	public void setQuantity(String quantity) { this.quantity = quantity; }
	public String getRate() { return rate; }
	public void setRate(String rate) { this.rate = rate; }
	public String getRecurrence() { return recurrence; }
	public void setRecurrence(String recurrence) { this.recurrence = recurrence; }
	public String getActive() { return active; }
	public void setActive(String active) { this.active = active; }
	public String getRemarks() { return remarks; }
	public void setRemarks(String remarks) { this.remarks = remarks; }
}
