package com.wagepayroll.liquibase.task;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import liquibase.change.custom.CustomTaskChange;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.CustomChangeException;
import liquibase.exception.SetupException;
import liquibase.exception.ValidationErrors;
import liquibase.resource.ResourceAccessor;

/**
 * M7 reference-data currencies: privilege catalog rows + demo tenant role grants.
 */
public class DataM7CurrencyPrivileges1 implements CustomTaskChange {

	private static final UUID TENANT_DEMO = UUID.fromString("10000000-0000-0000-0000-000000000001");
	private static final UUID ROLE_ADMIN = UUID.fromString("40000000-0000-0000-0000-000000000001");

	private static final UUID PRIV_PLATFORM_CURRENCY_VIEW = UUID.fromString("20000000-0000-0000-0000-000000000009");
	private static final UUID PRIV_PLATFORM_CURRENCY_EDIT = UUID.fromString("20000000-0000-0000-0000-000000000010");
	private static final UUID PRIV_TENANT_CURRENCY_VIEW = UUID.fromString("20000000-0000-0000-0000-000000000011");
	private static final UUID PRIV_TENANT_CURRENCY_EDIT = UUID.fromString("20000000-0000-0000-0000-000000000012");

	@Override
	public void execute(Database database) throws CustomChangeException {
		Instant now = Instant.now();
		Timestamp ts = Timestamp.from(now);
		try {
			Connection c = ((JdbcConnection) database.getConnection()).getUnderlyingConnection();
			c.setAutoCommit(false);
			try {
				insertPrivilege(c, PRIV_PLATFORM_CURRENCY_VIEW, "PLATFORM_CURRENCY_VIEW",
						"View platform currency catalog", ts);
				insertPrivilege(c, PRIV_PLATFORM_CURRENCY_EDIT, "PLATFORM_CURRENCY_EDIT",
						"Create and update platform currency catalog", ts);
				insertPrivilege(c, PRIV_TENANT_CURRENCY_VIEW, "TENANT_CURRENCY_VIEW",
						"View tenant currency assignments", ts);
				insertPrivilege(c, PRIV_TENANT_CURRENCY_EDIT, "TENANT_CURRENCY_EDIT",
						"Assign platform currencies to a tenant", ts);

				insertRolePrivilege(c, UUID.randomUUID(), TENANT_DEMO, ROLE_ADMIN, PRIV_TENANT_CURRENCY_VIEW);
				insertRolePrivilege(c, UUID.randomUUID(), TENANT_DEMO, ROLE_ADMIN, PRIV_TENANT_CURRENCY_EDIT);

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

	private static void insertPrivilege(Connection c, UUID id, String code, String desc, Timestamp ts) throws Exception {
		try (PreparedStatement ps = c.prepareStatement(
				"INSERT INTO privilege (id, code, description, created_at, updated_at) VALUES (?,?,?,?,?)")) {
			ps.setString(1, id.toString());
			ps.setString(2, code);
			ps.setString(3, desc);
			ps.setTimestamp(4, ts);
			ps.setTimestamp(5, ts);
			ps.executeUpdate();
		}
	}

	private static void insertRolePrivilege(Connection c, UUID id, UUID tenantId, UUID roleId, UUID privId)
			throws Exception {
		try (PreparedStatement ps = c.prepareStatement(
				"INSERT INTO role_privilege (id, tenant_id, role_id, privilege_id) VALUES (?,?,?,?)")) {
			ps.setString(1, id.toString());
			ps.setString(2, tenantId.toString());
			ps.setString(3, roleId.toString());
			ps.setString(4, privId.toString());
			ps.executeUpdate();
		}
	}

	@Override
	public String getConfirmationMessage() {
		return "M7 currency privileges seeded";
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
