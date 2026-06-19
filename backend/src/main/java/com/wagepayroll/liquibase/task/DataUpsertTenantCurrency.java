package com.wagepayroll.liquibase.task;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Upserts one {@code tenant_currency} link (tenant enabled for a platform currency).
 */
public class DataUpsertTenantCurrency extends CustomDataTaskChange {

	private String id;
	private String tenantId;
	private String platformCurrencyId;

	@Override
	public void handleUpdate() throws Exception {
		try (PreparedStatement check = connection.prepareStatement(
				"SELECT COUNT(*) FROM tenant_currency WHERE id = ?")) {
			setData(check, 1, id);
			try (ResultSet rs = check.executeQuery()) {
				rs.next();
				if (rs.getInt(1) > 0) {
					try (PreparedStatement ps = connection.prepareStatement("""
							UPDATE tenant_currency
							SET tenant_id = ?, platform_currency_id = ?, updated_at = ?
							WHERE id = ?
							""")) {
						setData(ps, 1, tenantId);
						setData(ps, 2, platformCurrencyId);
						setData(ps, 3, ts);
						setData(ps, 4, id);
						ps.executeUpdate();
					}
					return;
				}
			}
		}
		try (PreparedStatement ps = connection.prepareStatement("""
				INSERT INTO tenant_currency (id, tenant_id, platform_currency_id, created_at, updated_at)
				VALUES (?,?,?,?,?)
				""")) {
			setData(ps, 1, id);
			setData(ps, 2, tenantId);
			setData(ps, 3, platformCurrencyId);
			setData(ps, 4, ts);
			setData(ps, 5, ts);
			ps.executeUpdate();
		}
	}

	public String getId() { return id; }
	public void setId(String id) { this.id = id; }

	public String getTenantId() { return tenantId; }
	public void setTenantId(String tenantId) { this.tenantId = tenantId; }

	public String getPlatformCurrencyId() { return platformCurrencyId; }
	public void setPlatformCurrencyId(String platformCurrencyId) { this.platformCurrencyId = platformCurrencyId; }
}
