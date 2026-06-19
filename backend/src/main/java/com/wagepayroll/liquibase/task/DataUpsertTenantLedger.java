package com.wagepayroll.liquibase.task;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

/** Upserts {@code tenant_ledger}. */
public class DataUpsertTenantLedger extends CustomDataTaskChange {

	private String id;
	private String tenantId;
	private String companyId;
	private String platformLedgerTemplateId;
	private String code;
	private String description;
	private String active;

	@Override
	public void handleUpdate() throws Exception {
		boolean activeBool = active == null || active.isBlank() || Boolean.parseBoolean(active.trim());
		try (PreparedStatement check = connection.prepareStatement("SELECT COUNT(*) FROM tenant_ledger WHERE id = ?")) {
			setData(check, 1, id);
			try (ResultSet rs = check.executeQuery()) {
				rs.next();
				if (rs.getInt(1) > 0) {
					try (PreparedStatement ps = connection.prepareStatement("""
							UPDATE tenant_ledger SET
							  tenant_id = ?, company_id = ?, platform_ledger_template_id = ?, code = ?, description = ?,
							  active = ?, updated_at = ?
							WHERE id = ?
							""")) {
						int i = 1;
						setData(ps, i++, tenantId);
						setData(ps, i++, companyId);
						setData(ps, i++, platformLedgerTemplateId);
						setData(ps, i++, code);
						setData(ps, i++, description);
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
				INSERT INTO tenant_ledger (
				  id, tenant_id, company_id, platform_ledger_template_id, code, description, active, created_at, updated_at
				) VALUES (?,?,?,?,?,?,?,?,?)
				""")) {
			int i = 1;
			setData(ps, i++, id);
			setData(ps, i++, tenantId);
			setData(ps, i++, companyId);
			setData(ps, i++, platformLedgerTemplateId);
			setData(ps, i++, code);
			setData(ps, i++, description);
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
	public String getPlatformLedgerTemplateId() { return platformLedgerTemplateId; }
	public void setPlatformLedgerTemplateId(String platformLedgerTemplateId) { this.platformLedgerTemplateId = platformLedgerTemplateId; }
	public String getCode() { return code; }
	public void setCode(String code) { this.code = code; }
	public String getDescription() { return description; }
	public void setDescription(String description) { this.description = description; }
	public String getActive() { return active; }
	public void setActive(String active) { this.active = active; }
}
