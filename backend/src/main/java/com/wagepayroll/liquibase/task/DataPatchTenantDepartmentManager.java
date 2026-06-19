package com.wagepayroll.liquibase.task;

import java.sql.PreparedStatement;

/**
 * Sets {@code tenant_department.manager_employee_id} for an existing department row.
 * Blank {@code managerEmployeeId} clears the manager.
 */
public class DataPatchTenantDepartmentManager extends CustomDataTaskChange {

	private String tenantId;
	private String companyId;
	private String departmentId;
	private String managerEmployeeId;

	@Override
	public void handleUpdate() throws Exception {
		try (PreparedStatement ps = connection.prepareStatement("""
				UPDATE tenant_department
				SET manager_employee_id = ?, updated_at = ?
				WHERE id = ? AND tenant_id = ? AND company_id = ?
				""")) {
			setData(ps, 1, managerEmployeeId);
			setData(ps, 2, ts);
			setData(ps, 3, departmentId);
			setData(ps, 4, tenantId);
			setData(ps, 5, companyId);
			ps.executeUpdate();
		}
	}

	public String getTenantId() { return tenantId; }
	public void setTenantId(String tenantId) { this.tenantId = tenantId; }
	public String getCompanyId() { return companyId; }
	public void setCompanyId(String companyId) { this.companyId = companyId; }
	public String getDepartmentId() { return departmentId; }
	public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }
	public String getManagerEmployeeId() { return managerEmployeeId; }
	public void setManagerEmployeeId(String managerEmployeeId) { this.managerEmployeeId = managerEmployeeId; }
}
