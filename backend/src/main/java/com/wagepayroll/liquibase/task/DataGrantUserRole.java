package com.wagepayroll.liquibase.task;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

/**
 * Inserts a row into {@code user_role} if the composite key
 * {@code (tenant_id, user_id, role_id)} does not yet exist.
 *
 * <pre>{@code
 * <customChange class="com.wagepayroll.liquibase.task.DataGrantUserRole">
 *     <param name="tenantId" value="10000000-0000-0000-0000-000000000001"/>
 *     <param name="userId"   value="30000000-0000-0000-0000-000000000001"/>
 *     <param name="roleId"   value="40000000-0000-0000-0000-000000000001"/>
 * </customChange>
 * }</pre>
 */
public class DataGrantUserRole extends CustomDataTaskChange {

	private String tenantId;
	private String userId;
	private String roleId;

	@Override
	public void handleUpdate() throws Exception {
		try (PreparedStatement check = connection.prepareStatement(
				"SELECT COUNT(*) FROM user_role WHERE tenant_id = ? AND user_id = ? AND role_id = ?")) {
			setData(check, 1, tenantId);
			setData(check, 2, userId);
			setData(check, 3, roleId);
			try (ResultSet rs = check.executeQuery()) {
				rs.next();
				if (rs.getInt(1) > 0) return;
			}
		}
		try (PreparedStatement ps = connection.prepareStatement(
				"INSERT INTO user_role (id, tenant_id, user_id, role_id, created_at, updated_at) VALUES (?,?,?,?,?,?)")) {
			setData(ps, 1, UUID.randomUUID().toString());
			setData(ps, 2, tenantId);
			setData(ps, 3, userId);
			setData(ps, 4, roleId);
			setData(ps, 5, ts);
			setData(ps, 6, ts);
			ps.executeUpdate();
		}
	}

	public String getTenantId() { return tenantId; }
	public void setTenantId(String tenantId) { this.tenantId = tenantId; }

	public String getUserId() { return userId; }
	public void setUserId(String userId) { this.userId = userId; }

	public String getRoleId() { return roleId; }
	public void setRoleId(String roleId) { this.roleId = roleId; }
}
