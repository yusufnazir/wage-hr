package com.wagepayroll.liquibase.task;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

/** Upserts {@code tenant_department}. */
public class DataUpsertTenantDepartment extends CustomDataTaskChange {

	private String id;
	private String tenantId;
	private String companyId;
	private String name;
	private String code;
	private String description;
	private String parentDepartmentId;
	private String managerEmployeeId;
	private String active;

	@Override
	public void handleUpdate() throws Exception {
		boolean activeBool = active == null || active.isBlank() || Boolean.parseBoolean(active.trim());
		try (PreparedStatement check = connection.prepareStatement("SELECT COUNT(*) FROM tenant_department WHERE id = ?")) {
			setData(check, 1, id);
			try (ResultSet rs = check.executeQuery()) {
				rs.next();
				if (rs.getInt(1) > 0) {
					try (PreparedStatement ps = connection.prepareStatement("""
							UPDATE tenant_department SET
							  tenant_id = ?, company_id = ?, name = ?, code = ?, description = ?,
							  parent_department_id = ?, manager_employee_id = ?, active = ?, updated_at = ?
							WHERE id = ?
							""")) {
						int i = 1;
						setData(ps, i++, tenantId);
						setData(ps, i++, companyId);
						setData(ps, i++, name);
						setData(ps, i++, code);
						setData(ps, i++, description);
						setData(ps, i++, parentDepartmentId);
						setData(ps, i++, managerEmployeeId);
						ps.setBoolean(i++, activeBool);
						setData(ps, i++, ts);
						setData(ps, i++, id);
						ps.executeUpdate();
					}
					return;
				}
			}
		}
		try (PreparedStatement ps = connection.prepareStatement("""
				INSERT INTO tenant_department (
				  id, tenant_id, company_id, name, code, description, parent_department_id, manager_employee_id,
				  active, created_at, updated_at
				) VALUES (?,?,?,?,?,?,?,?,?,?,?)
				""")) {
			int i = 1;
			setData(ps, i++, id);
			setData(ps, i++, tenantId);
			setData(ps, i++, companyId);
			setData(ps, i++, name);
			setData(ps, i++, code);
			setData(ps, i++, description);
			setData(ps, i++, parentDepartmentId);
			setData(ps, i++, managerEmployeeId);
			ps.setBoolean(i++, activeBool);
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
	public String getName() { return name; }
	public void setName(String name) { this.name = name; }
	public String getCode() { return code; }
	public void setCode(String code) { this.code = code; }
	public String getDescription() { return description; }
	public void setDescription(String description) { this.description = description; }
	public String getParentDepartmentId() { return parentDepartmentId; }
	public void setParentDepartmentId(String parentDepartmentId) { this.parentDepartmentId = parentDepartmentId; }
	public String getManagerEmployeeId() { return managerEmployeeId; }
	public void setManagerEmployeeId(String managerEmployeeId) { this.managerEmployeeId = managerEmployeeId; }
	public String getActive() { return active; }
	public void setActive(String active) { this.active = active; }
}
