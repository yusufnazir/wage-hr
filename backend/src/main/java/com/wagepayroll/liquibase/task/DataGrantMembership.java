package com.wagepayroll.liquibase.task;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

/**
 * Inserts a row into {@code membership} if the composite key
 * {@code (tenant_id, user_id)} does not yet exist.
 *
 * <pre>{@code
 * <customChange class="com.wagepayroll.liquibase.task.DataGrantMembership">
 *     <param name="tenantId" value="10000000-0000-0000-0000-000000000001"/>
 *     <param name="userId"   value="30000000-0000-0000-0000-000000000001"/>
 * </customChange>
 * }</pre>
 */
public class DataGrantMembership extends CustomDataTaskChange {

	private String tenantId;
	private String userId;

	@Override
	public void handleUpdate() throws Exception {
		try (PreparedStatement check = connection.prepareStatement(
				"SELECT COUNT(*) FROM membership WHERE tenant_id = ? AND user_id = ?")) {
			setData(check, 1, tenantId);
			setData(check, 2, userId);
			try (ResultSet rs = check.executeQuery()) {
				rs.next();
				if (rs.getInt(1) > 0) return;
			}
		}
		try (PreparedStatement ps = connection.prepareStatement(
				"INSERT INTO membership (id, tenant_id, user_id, created_at, updated_at) VALUES (?,?,?,?,?)")) {
			setData(ps, 1, UUID.randomUUID().toString());
			setData(ps, 2, tenantId);
			setData(ps, 3, userId);
			setData(ps, 4, ts);
			setData(ps, 5, ts);
			ps.executeUpdate();
		}
	}

	public String getTenantId() { return tenantId; }
	public void setTenantId(String tenantId) { this.tenantId = tenantId; }

	public String getUserId() { return userId; }
	public void setUserId(String userId) { this.userId = userId; }
}
