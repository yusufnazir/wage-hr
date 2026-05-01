package com.wagepayroll.liquibase.task;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

import liquibase.change.custom.CustomTaskChange;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.CustomChangeException;
import liquibase.exception.SetupException;
import liquibase.exception.ValidationErrors;
import liquibase.resource.ResourceAccessor;

/**
 * Adds tenant currency privileges to ADMIN role template for future tenant bootstrap.
 */
public class DataM7RoleTemplateCurrencyPrivileges1 implements CustomTaskChange {

	@Override
	public void execute(Database database) throws CustomChangeException {
		try {
			Connection c = ((JdbcConnection) database.getConnection()).getUnderlyingConnection();
			c.setAutoCommit(false);
			try {
				UUID adminTemplateId = resolveTemplateId(c, "ADMIN");
				UUID tenantCurrencyView = resolvePrivilegeId(c, "TENANT_CURRENCY_VIEW");
				UUID tenantCurrencyEdit = resolvePrivilegeId(c, "TENANT_CURRENCY_EDIT");
				insertIfMissing(c, adminTemplateId, tenantCurrencyView);
				insertIfMissing(c, adminTemplateId, tenantCurrencyEdit);
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

	private static UUID resolveTemplateId(Connection c, String code) throws Exception {
		try (PreparedStatement ps = c.prepareStatement("SELECT id FROM role_template WHERE code = ?")) {
			ps.setString(1, code);
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) {
					throw new IllegalStateException("Missing role template: " + code);
				}
				return UUID.fromString(rs.getString(1));
			}
		}
	}

	private static UUID resolvePrivilegeId(Connection c, String code) throws Exception {
		try (PreparedStatement ps = c.prepareStatement("SELECT id FROM privilege WHERE code = ?")) {
			ps.setString(1, code);
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) {
					throw new IllegalStateException("Missing privilege row: " + code);
				}
				return UUID.fromString(rs.getString(1));
			}
		}
	}

	private static void insertIfMissing(Connection c, UUID templateId, UUID privilegeId) throws Exception {
		try (PreparedStatement check = c.prepareStatement(
				"SELECT count(*) FROM role_template_privilege WHERE role_template_id = ? AND privilege_id = ?")) {
			check.setString(1, templateId.toString());
			check.setString(2, privilegeId.toString());
			try (ResultSet rs = check.executeQuery()) {
				rs.next();
				if (rs.getLong(1) > 0) {
					return;
				}
			}
		}
		try (PreparedStatement insert = c.prepareStatement(
				"INSERT INTO role_template_privilege (id, role_template_id, privilege_id) VALUES (?,?,?)")) {
			insert.setString(1, UUID.randomUUID().toString());
			insert.setString(2, templateId.toString());
			insert.setString(3, privilegeId.toString());
			insert.executeUpdate();
		}
	}

	@Override
	public String getConfirmationMessage() {
		return "M7 role template currency privileges seeded";
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
