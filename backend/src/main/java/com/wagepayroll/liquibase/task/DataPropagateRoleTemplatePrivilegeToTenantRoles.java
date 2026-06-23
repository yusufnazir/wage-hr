package com.wagepayroll.liquibase.task;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

/**
 * Grants a privilege to every tenant {@code role} whose {@code name} matches the
 * {@code display_name} of the given {@code role_template.code}. Idempotent.
 *
 * <pre>{@code
 * <customChange class="com.wagepayroll.liquibase.task.DataPropagateRoleTemplatePrivilegeToTenantRoles">
 *     <param name="roleTemplateCode" value="ADMIN"/>
 *     <param name="privilegeCode" value="PAYMENT_LOCATION_VIEW"/>
 * </customChange>
 * }</pre>
 */
public class DataPropagateRoleTemplatePrivilegeToTenantRoles extends CustomDataTaskChange {

	private String roleTemplateCode;
	private String privilegeCode;

	@Override
	public void handleUpdate() throws Exception {
		String roleDisplayName;
		try (PreparedStatement lookup = connection.prepareStatement(
				"SELECT display_name FROM role_template WHERE code = ?")) {
			setData(lookup, 1, roleTemplateCode);
			try (ResultSet rs = lookup.executeQuery()) {
				if (!rs.next()) {
					throw new IllegalStateException("Missing role template: " + roleTemplateCode);
				}
				roleDisplayName = rs.getString(1);
			}
		}

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

		try (PreparedStatement roles = connection.prepareStatement(
				"SELECT id, tenant_id FROM role WHERE name = ?")) {
			setData(roles, 1, roleDisplayName);
			try (ResultSet rs = roles.executeQuery()) {
				while (rs.next()) {
					grantIfMissing(rs.getString("tenant_id"), rs.getString("id"), privilegeId);
				}
			}
		}
	}

	private void grantIfMissing(String tenantId, String roleId, String privilegeId) throws Exception {
		try (PreparedStatement check = connection.prepareStatement(
				"SELECT COUNT(*) FROM role_privilege WHERE tenant_id = ? AND role_id = ? AND privilege_id = ?")) {
			setData(check, 1, tenantId);
			setData(check, 2, roleId);
			setData(check, 3, privilegeId);
			try (ResultSet rs = check.executeQuery()) {
				rs.next();
				if (rs.getInt(1) > 0) {
					return;
				}
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

	public String getRoleTemplateCode() {
		return roleTemplateCode;
	}

	public void setRoleTemplateCode(String roleTemplateCode) {
		this.roleTemplateCode = roleTemplateCode;
	}

	public String getPrivilegeCode() {
		return privilegeCode;
	}

	public void setPrivilegeCode(String privilegeCode) {
		this.privilegeCode = privilegeCode;
	}
}
