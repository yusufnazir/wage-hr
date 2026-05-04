package com.wagepayroll.liquibase.task;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

/**
 * Inserts a row into {@code role_template_privilege} if the composite key
 * {@code (role_template_id, privilege_id)} does not yet exist.
 * The privilege is resolved by {@code privilegeCode} at runtime.
 *
 * <pre>{@code
 * <customChange class="com.wagepayroll.liquibase.task.DataGrantRoleTemplatePrivilege">
 *     <param name="roleTemplateId" value="60000000-0000-0000-0000-000000000001"/>
 *     <param name="privilegeCode"  value="USER_VIEW"/>
 * </customChange>
 * }</pre>
 */
public class DataGrantRoleTemplatePrivilege extends CustomDataTaskChange {

	private String roleTemplateId;
	private String privilegeCode;

	@Override
	public void handleUpdate() throws Exception {
		String privilegeId;
		try (PreparedStatement lookup = connection.prepareStatement(
				"SELECT id FROM privilege WHERE code = ?")) {
			setData(lookup, 1, privilegeCode);
			try (ResultSet rs = lookup.executeQuery()) {
				if (!rs.next()) {
					throw new IllegalStateException("Missing privilege row for code: " + privilegeCode);
				}
				privilegeId = rs.getString(1);
			}
		}

		try (PreparedStatement check = connection.prepareStatement(
				"SELECT COUNT(*) FROM role_template_privilege WHERE role_template_id = ? AND privilege_id = ?")) {
			setData(check, 1, roleTemplateId);
			setData(check, 2, privilegeId);
			try (ResultSet rs = check.executeQuery()) {
				rs.next();
				if (rs.getInt(1) > 0) return;
			}
		}
		try (PreparedStatement ps = connection.prepareStatement(
				"INSERT INTO role_template_privilege (id, role_template_id, privilege_id) VALUES (?,?,?)")) {
			setData(ps, 1, UUID.randomUUID().toString());
			setData(ps, 2, roleTemplateId);
			setData(ps, 3, privilegeId);
			ps.executeUpdate();
		}
	}

	public String getRoleTemplateId() { return roleTemplateId; }
	public void setRoleTemplateId(String roleTemplateId) { this.roleTemplateId = roleTemplateId; }

	public String getPrivilegeCode() { return privilegeCode; }
	public void setPrivilegeCode(String privilegeCode) { this.privilegeCode = privilegeCode; }
}
