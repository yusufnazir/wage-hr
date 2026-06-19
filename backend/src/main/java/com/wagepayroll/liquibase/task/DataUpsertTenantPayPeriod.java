package com.wagepayroll.liquibase.task;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

/** Upserts {@code tenant_pay_period}. */
public class DataUpsertTenantPayPeriod extends CustomDataTaskChange {

	private String id;
	private String tenantId;
	private String companyId;
	private String year;
	private String startDate;
	private String endDate;
	private String status;

	@Override
	public void handleUpdate() throws Exception {
		int y = Integer.parseInt(year.trim());
		try (PreparedStatement check = connection.prepareStatement("SELECT COUNT(*) FROM tenant_pay_period WHERE id = ?")) {
			setData(check, 1, id);
			try (ResultSet rs = check.executeQuery()) {
				rs.next();
				if (rs.getInt(1) > 0) {
					try (PreparedStatement ps = connection.prepareStatement("""
							UPDATE tenant_pay_period SET
							  tenant_id = ?, company_id = ?, `year` = ?, start_date = ?, end_date = ?, status = ?, updated_at = ?
							WHERE id = ?
							""")) {
						int i = 1;
						setData(ps, i++, tenantId);
						setData(ps, i++, companyId);
						ps.setInt(i++, y);
						setDate(ps, i++, startDate);
						setDate(ps, i++, endDate);
						setData(ps, i++, status);
						setData(ps, i++, ts);
						setData(ps, i++, id);
						ps.executeUpdate();
					}
					return;
				}
			}
		}
		try (PreparedStatement ps = connection.prepareStatement("""
				INSERT INTO tenant_pay_period (id, tenant_id, company_id, `year`, start_date, end_date, status, created_at, updated_at)
				VALUES (?,?,?,?,?,?,?,?,?)
				""")) {
			int i = 1;
			setData(ps, i++, id);
			setData(ps, i++, tenantId);
			setData(ps, i++, companyId);
			ps.setInt(i++, y);
			setDate(ps, i++, startDate);
			setDate(ps, i++, endDate);
			setData(ps, i++, status);
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
	public String getYear() { return year; }
	public void setYear(String year) { this.year = year; }
	public String getStartDate() { return startDate; }
	public void setStartDate(String startDate) { this.startDate = startDate; }
	public String getEndDate() { return endDate; }
	public void setEndDate(String endDate) { this.endDate = endDate; }
	public String getStatus() { return status; }
	public void setStatus(String status) { this.status = status; }
}
