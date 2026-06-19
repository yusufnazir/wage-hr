package com.wagepayroll.liquibase.task;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

/** Upserts {@code tenant_job}. */
public class DataUpsertTenantJob extends CustomDataTaskChange {

	private String id;
	private String tenantId;
	private String companyId;
	private String departmentId;
	private String title;
	private String code;
	private String description;
	private String salaryType;
	private String defaultSalary;
	private String defaultHourlyRate;
	private String standardHoursPerWeek;
	private String jobLevel;
	private String jobCategory;
	private String active;

	@Override
	public void handleUpdate() throws Exception {
		boolean activeBool = active == null || active.isBlank() || Boolean.parseBoolean(active.trim());
		try (PreparedStatement check = connection.prepareStatement("SELECT COUNT(*) FROM tenant_job WHERE id = ?")) {
			setData(check, 1, id);
			try (ResultSet rs = check.executeQuery()) {
				rs.next();
				if (rs.getInt(1) > 0) {
					try (PreparedStatement ps = connection.prepareStatement("""
							UPDATE tenant_job SET
							  tenant_id = ?, company_id = ?, department_id = ?, title = ?, code = ?, description = ?,
							  salary_type = ?, default_salary = ?, default_hourly_rate = ?, standard_hours_per_week = ?,
							  job_level = ?, job_category = ?, active = ?, updated_at = ?
							WHERE id = ?
							""")) {
						int i = 1;
						setData(ps, i++, tenantId);
						setData(ps, i++, companyId);
						setData(ps, i++, departmentId);
						setData(ps, i++, title);
						setData(ps, i++, code);
						setData(ps, i++, description);
						setData(ps, i++, salaryType);
						setDecimal(ps, i++, defaultSalary);
						setDecimal(ps, i++, defaultHourlyRate);
						setDecimal(ps, i++, standardHoursPerWeek);
						setData(ps, i++, jobLevel);
						setData(ps, i++, jobCategory);
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
				INSERT INTO tenant_job (
				  id, tenant_id, company_id, department_id, title, code, description,
				  salary_type, default_salary, default_hourly_rate, standard_hours_per_week,
				  job_level, job_category, active, created_at, updated_at
				) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
				""")) {
			int i = 1;
			setData(ps, i++, id);
			setData(ps, i++, tenantId);
			setData(ps, i++, companyId);
			setData(ps, i++, departmentId);
			setData(ps, i++, title);
			setData(ps, i++, code);
			setData(ps, i++, description);
			setData(ps, i++, salaryType);
			setDecimal(ps, i++, defaultSalary);
			setDecimal(ps, i++, defaultHourlyRate);
			setDecimal(ps, i++, standardHoursPerWeek);
			setData(ps, i++, jobLevel);
			setData(ps, i++, jobCategory);
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
	public String getDepartmentId() { return departmentId; }
	public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }
	public String getTitle() { return title; }
	public void setTitle(String title) { this.title = title; }
	public String getCode() { return code; }
	public void setCode(String code) { this.code = code; }
	public String getDescription() { return description; }
	public void setDescription(String description) { this.description = description; }
	public String getSalaryType() { return salaryType; }
	public void setSalaryType(String salaryType) { this.salaryType = salaryType; }
	public String getDefaultSalary() { return defaultSalary; }
	public void setDefaultSalary(String defaultSalary) { this.defaultSalary = defaultSalary; }
	public String getDefaultHourlyRate() { return defaultHourlyRate; }
	public void setDefaultHourlyRate(String defaultHourlyRate) { this.defaultHourlyRate = defaultHourlyRate; }
	public String getStandardHoursPerWeek() { return standardHoursPerWeek; }
	public void setStandardHoursPerWeek(String standardHoursPerWeek) { this.standardHoursPerWeek = standardHoursPerWeek; }
	public String getJobLevel() { return jobLevel; }
	public void setJobLevel(String jobLevel) { this.jobLevel = jobLevel; }
	public String getJobCategory() { return jobCategory; }
	public void setJobCategory(String jobCategory) { this.jobCategory = jobCategory; }
	public String getActive() { return active; }
	public void setActive(String active) { this.active = active; }
}
