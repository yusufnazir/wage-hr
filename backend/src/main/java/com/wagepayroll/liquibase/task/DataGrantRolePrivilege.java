package com.wagepayroll.liquibase.task;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

/**
 * Inserts a row into {@code role_privilege} if the composite key
 * {@code (tenant_id, role_id, privilege_id)} does not yet exist.
 *
 * <pre>{@code
 * <customChange class="com.wagepayroll.liquibase.task.DataGrantRolePrivilege">
 *     <param name="tenantId"    value="10000000-0000-0000-0000-000000000001"/>
 *     <param name="roleId"      value="40000000-0000-0000-0000-000000000001"/>
 *     <param name="privilegeId" value="20000000-0000-0000-0000-000000000001"/>
 * </customChange>
 * }</pre>
 */
public class DataGrantRolePrivilege extends CustomDataTaskChange {

	private String tenantId;
	private String roleId;
	private String privilegeId;

	@Override
	public void handleUpdate() throws Exception {
		try (PreparedStatement check = connection.prepareStatement(
				"SELECT COUNT(*) FROM role_privilege WHERE tenant_id = ? AND role_id = ? AND privilege_id = ?")) {
			setData(check, 1, tenantId);
			setData(check, 2, roleId);
			setData(check, 3, privilegeId);
			try (ResultSet rs = check.executeQuery()) {
				rs.next();
				if (rs.getInt(1) > 0) return;
			}
		}
		try (PreparedStatement ps = connection.prepareStatement(
				"INSERT INTO role_privilege (id, tenant_id, role_id, privilege_id) VALUES (?,?,?,?)")) {
			setData(ps, 1, UUID.randomUUID().toString());
			setData(ps, 2, tenantId);
			setData(ps, 3, roleId);
			setData(ps, 4, privilegeId);
			ps.executeUpdate();
		}
	}

	public String getTenantId() { return tenantId; }
	public void setTenantId(String tenantId) { this.tenantId = tenantId; }

	public String getRoleId() { return roleId; }
	public void setRoleId(String roleId) { this.roleId = roleId; }

	public String getPrivilegeId() { return privilegeId; }
	public void setPrivilegeId(String privilegeId) { this.privilegeId = privilegeId; }
}
