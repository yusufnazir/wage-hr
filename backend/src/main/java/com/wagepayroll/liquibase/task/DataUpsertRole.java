package com.wagepayroll.liquibase.task;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Upserts one row in the {@code role} table.
 *
 * <pre>{@code
 * <customChange class="com.wagepayroll.liquibase.task.DataUpsertRole">
 *     <param name="id"       value="40000000-0000-0000-0000-000000000001"/>
 *     <param name="tenantId" value="10000000-0000-0000-0000-000000000001"/>
 *     <param name="name"     value="Admin"/>
 * </customChange>
 * }</pre>
 */
public class DataUpsertRole extends CustomDataTaskChange {

	private String id;
	private String tenantId;
	private String name;

	@Override
	public void handleUpdate() throws Exception {
		try (PreparedStatement check = connection.prepareStatement(
				"SELECT COUNT(*) FROM role WHERE id = ?")) {
			setData(check, 1, id);
			try (ResultSet rs = check.executeQuery()) {
				rs.next();
				if (rs.getInt(1) > 0) {
					try (PreparedStatement ps = connection.prepareStatement(
							"UPDATE role SET name = ?, updated_at = ? WHERE id = ?")) {
						setData(ps, 1, name);
						setData(ps, 2, ts);
						setData(ps, 3, id);
						ps.executeUpdate();
					}
					return;
				}
			}
		}
		try (PreparedStatement ps = connection.prepareStatement(
				"INSERT INTO role (id, tenant_id, name, created_at, updated_at) VALUES (?,?,?,?,?)")) {
			setData(ps, 1, id);
			setData(ps, 2, tenantId);
			setData(ps, 3, name);
			setData(ps, 4, ts);
			setData(ps, 5, ts);
			ps.executeUpdate();
		}
	}

	public String getId() { return id; }
	public void setId(String id) { this.id = id; }

	public String getTenantId() { return tenantId; }
	public void setTenantId(String tenantId) { this.tenantId = tenantId; }

	public String getName() { return name; }
	public void setName(String name) { this.name = name; }
}
