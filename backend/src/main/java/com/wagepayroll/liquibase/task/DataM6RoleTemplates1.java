package com.wagepayroll.liquibase.task;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import liquibase.change.custom.CustomTaskChange;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.CustomChangeException;
import liquibase.exception.SetupException;
import liquibase.exception.ValidationErrors;
import liquibase.resource.ResourceAccessor;

/**
 * M6: platform role templates (ADMIN, EMPLOYEE).
 */
public class DataM6RoleTemplates1 implements CustomTaskChange {

	private static final UUID TEMPLATE_ADMIN = UUID.fromString("60000000-0000-0000-0000-000000000001");
	private static final UUID TEMPLATE_EMPLOYEE = UUID.fromString("60000000-0000-0000-0000-000000000002");

	@Override
	public void execute(Database database) throws CustomChangeException {
		Instant now = Instant.now();
		Timestamp ts = Timestamp.from(now);

		try {
			Connection c = ((JdbcConnection) database.getConnection()).getUnderlyingConnection();
			c.setAutoCommit(false);
			try {
				upsertTemplate(c, TEMPLATE_ADMIN, "ADMIN", "Admin", ts);
				upsertTemplate(c, TEMPLATE_EMPLOYEE, "EMPLOYEE", "Employee", ts);

				// v1: keep sets small and aligned to shipped privilege catalog.
				seedTemplatePrivilegesByCode(c, TEMPLATE_ADMIN, List.of("USER_VIEW", "USER_EDIT", "USER_INVITE",
						"TENANT_SETTINGS_EDIT", "ROLE_VIEW", "ROLE_EDIT", "DOCUMENT_VIEW", "DOCUMENT_EDIT"));
				seedTemplatePrivilegesByCode(c, TEMPLATE_EMPLOYEE, List.of("DOCUMENT_VIEW"));

				c.commit();
			}
			catch (Exception e) {
				c.rollback();
				throw e;
			}
		}
		catch (Exception e) {
			throw new CustomChangeException(e.getMessage(), e);
		}
	}

	private static void upsertTemplate(Connection c, UUID id, String code, String displayName, Timestamp ts)
			throws Exception {
		try (PreparedStatement check = c.prepareStatement("SELECT COUNT(*) FROM role_template WHERE id = ?")) {
			check.setString(1, id.toString());
			try (ResultSet rs = check.executeQuery()) {
				rs.next();
				if (rs.getInt(1) > 0) {
					try (PreparedStatement ps = c.prepareStatement(
							"UPDATE role_template SET code = ?, display_name = ?, updated_at = ? WHERE id = ?")) {
						ps.setString(1, code);
						ps.setString(2, displayName);
						ps.setTimestamp(3, ts);
						ps.setString(4, id.toString());
						ps.executeUpdate();
					}
					return;
				}
			}
		}
		try (PreparedStatement ps = c.prepareStatement(
				"INSERT INTO role_template (id, code, display_name, created_at, updated_at) VALUES (?,?,?,?,?)")) {
			ps.setString(1, id.toString());
			ps.setString(2, code);
			ps.setString(3, displayName);
			ps.setTimestamp(4, ts);
			ps.setTimestamp(5, ts);
			ps.executeUpdate();
		}
	}

	private static void seedTemplatePrivilegesByCode(Connection c, UUID templateId, List<String> privilegeCodes)
			throws Exception {
		for (String code : privilegeCodes) {
			UUID privilegeId = resolvePrivilegeId(c, code);
			try (PreparedStatement check = c.prepareStatement(
					"SELECT COUNT(*) FROM role_template_privilege WHERE role_template_id = ? AND privilege_id = ?")) {
				check.setString(1, templateId.toString());
				check.setString(2, privilegeId.toString());
				try (ResultSet rs = check.executeQuery()) {
					rs.next();
					if (rs.getInt(1) > 0) continue;
				}
			}
			try (PreparedStatement ps = c.prepareStatement(
					"INSERT INTO role_template_privilege (id, role_template_id, privilege_id) VALUES (?,?,?)")) {
				ps.setString(1, UUID.randomUUID().toString());
				ps.setString(2, templateId.toString());
				ps.setString(3, privilegeId.toString());
				ps.executeUpdate();
			}
		}
	}

	private static UUID resolvePrivilegeId(Connection c, String code) throws Exception {
		try (PreparedStatement ps = c.prepareStatement("SELECT id FROM privilege WHERE code = ?")) {
			ps.setString(1, code);
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) {
					throw new IllegalStateException("Missing privilege row for code: " + code);
				}
				return UUID.fromString(rs.getString(1));
			}
		}
	}

	@Override
	public String getConfirmationMessage() {
		return "M6 role templates seeded";
	}

	@Override
	public void setUp() throws SetupException {
	}

	@Override
	public void setFileOpener(ResourceAccessor resourceAccessor) {
	}

	@Override
	public ValidationErrors validate(Database database) {
		return new ValidationErrors();
	}
}

