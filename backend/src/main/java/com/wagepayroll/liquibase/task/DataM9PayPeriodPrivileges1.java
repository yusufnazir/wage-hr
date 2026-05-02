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
 * M9 pay period privileges and nav menu item.
 */
public class DataM9PayPeriodPrivileges1 implements CustomTaskChange {

	private static final UUID TENANT_DEMO = UUID.fromString("10000000-0000-0000-0000-000000000001");
	private static final UUID ROLE_ADMIN = UUID.fromString("40000000-0000-0000-0000-000000000001");

	private static final UUID PRIV_PAY_PERIOD_VIEW = UUID.fromString("20000000-0000-0000-0000-000000000027");
	private static final UUID PRIV_PAY_PERIOD_MANAGE = UUID.fromString("20000000-0000-0000-0000-000000000028");
	private static final UUID PRIV_PAY_PERIOD_RUN_VIEW = UUID.fromString("20000000-0000-0000-0000-000000000029");
	private static final UUID PRIV_PAY_PERIOD_RUN_MANAGE = UUID.fromString("20000000-0000-0000-0000-000000000030");

	@Override
	public void execute(Database database) throws CustomChangeException {
		Instant now = Instant.now();
		Timestamp ts = Timestamp.from(now);
		try {
			Connection c = ((JdbcConnection) database.getConnection()).getUnderlyingConnection();
			c.setAutoCommit(false);
			try {
				insertPrivilege(c, PRIV_PAY_PERIOD_VIEW, "PAY_PERIOD_VIEW", "View pay periods", ts);
				insertPrivilege(c, PRIV_PAY_PERIOD_MANAGE, "PAY_PERIOD_MANAGE", "Create and manage pay periods", ts);
				insertPrivilege(c, PRIV_PAY_PERIOD_RUN_VIEW, "PAY_PERIOD_RUN_VIEW", "View pay period runs", ts);
				insertPrivilege(c, PRIV_PAY_PERIOD_RUN_MANAGE, "PAY_PERIOD_RUN_MANAGE", "Create and manage pay period runs", ts);

				insertRolePrivilege(c, UUID.randomUUID(), TENANT_DEMO, ROLE_ADMIN, PRIV_PAY_PERIOD_VIEW);
				insertRolePrivilege(c, UUID.randomUUID(), TENANT_DEMO, ROLE_ADMIN, PRIV_PAY_PERIOD_MANAGE);
				insertRolePrivilege(c, UUID.randomUUID(), TENANT_DEMO, ROLE_ADMIN, PRIV_PAY_PERIOD_RUN_VIEW);
				insertRolePrivilege(c, UUID.randomUUID(), TENANT_DEMO, ROLE_ADMIN, PRIV_PAY_PERIOD_RUN_MANAGE);

				insertNavMenuItem(c, "50000000-0000-0000-0000-000000000016", "/app/pay-periods", "nav.pay_periods", 50,
						"PAY_PERIOD_VIEW", ts);

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

	private static void insertNavMenuItem(Connection c, String id, String path, String labelKey, int sortOrder,
			String privilegeCode, Timestamp ts) throws Exception {
		try (PreparedStatement ps = c.prepareStatement(
				"INSERT INTO nav_menu_item (id, parent_id, path, label_key, sort_order, required_privilege_code, required_plan_feature_code, created_at, updated_at) VALUES (?,NULL,?,?,?,?,NULL,?,?)")) {
			ps.setString(1, id);
			ps.setString(2, path);
			ps.setString(3, labelKey);
			ps.setInt(4, sortOrder);
			ps.setString(5, privilegeCode);
			ps.setTimestamp(6, ts);
			ps.setTimestamp(7, ts);
			ps.executeUpdate();
		}
	}

	@Override
	public String getConfirmationMessage() {
		return "M9 pay period privileges and nav item seeded";
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
