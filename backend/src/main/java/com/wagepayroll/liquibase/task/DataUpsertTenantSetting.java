package com.wagepayroll.liquibase.task;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Upserts one row in the {@code tenant_setting} table.
 * Note: {@code key} is a reserved word in MariaDB/MySQL and must be quoted in raw JDBC.
 *
 * <pre>{@code
 * <customChange class="com.wagepayroll.liquibase.task.DataUpsertTenantSetting">
 *     <param name="id"        value="52000000-0000-0000-0000-000000000001"/>
 *     <param name="tenantId"  value="10000000-0000-0000-0000-000000000001"/>
 *     <param name="key"       value="tenant.demo_flag"/>
 *     <param name="valueText" value="1"/>
 * </customChange>
 * }</pre>
 */
public class DataUpsertTenantSetting extends CustomDataTaskChange {

	private String id;
	private String tenantId;
	private String key;
	private String valueText;

	@Override
	public void handleUpdate() throws Exception {
		try (PreparedStatement check = connection.prepareStatement(
				"SELECT COUNT(*) FROM tenant_setting WHERE id = ?")) {
			setData(check, 1, id);
			try (ResultSet rs = check.executeQuery()) {
				rs.next();
				if (rs.getInt(1) > 0) {
					try (PreparedStatement ps = connection.prepareStatement(
							"UPDATE tenant_setting SET `key` = ?, value_text = ?, updated_at = ? WHERE id = ?")) {
						setData(ps, 1, key);
						setData(ps, 2, valueText);
						setData(ps, 3, ts);
						setData(ps, 4, id);
						ps.executeUpdate();
					}
					return;
				}
			}
		}
		try (PreparedStatement ps = connection.prepareStatement(
				"INSERT INTO tenant_setting (id, tenant_id, `key`, value_text, created_at, updated_at) VALUES (?,?,?,?,?,?)")) {
			setData(ps, 1, id);
			setData(ps, 2, tenantId);
			setData(ps, 3, key);
			setData(ps, 4, valueText);
			setData(ps, 5, ts);
			setData(ps, 6, ts);
			ps.executeUpdate();
		}
	}

	public String getId() { return id; }
	public void setId(String id) { this.id = id; }

	public String getTenantId() { return tenantId; }
	public void setTenantId(String tenantId) { this.tenantId = tenantId; }

	public String getKey() { return key; }
	public void setKey(String key) { this.key = key; }

	public String getValueText() { return valueText; }
	public void setValueText(String valueText) { this.valueText = valueText; }
}
