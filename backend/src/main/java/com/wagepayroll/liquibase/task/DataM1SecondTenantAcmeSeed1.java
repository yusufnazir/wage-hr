package com.wagepayroll.liquibase.task;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.sql.Types;
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
 * Second seeded tenant {@code acme}: same admin user as demo, but **narrower** role (VIEW-only) to prove
 * roles differ per tenant. Reuses global {@code privilege} rows from {@link DataScaffoldSeed1}.
 */
public class DataM1SecondTenantAcmeSeed1 implements CustomTaskChange {

	private static final UUID ACME_TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
	private static final UUID USER_ADMIN = UUID.fromString("30000000-0000-0000-0000-000000000001");
	private static final UUID PRIV_USER_VIEW = UUID.fromString("20000000-0000-0000-0000-000000000001");

	private static final UUID MEMBERSHIP_ID = UUID.fromString("60000000-0000-0000-0000-000000000001");
	private static final UUID ROLE_READER_ID = UUID.fromString("40000000-0000-0000-0000-000000000003");
	private static final UUID TPA_VIEW_ID = UUID.fromString("61000000-0000-0000-0000-000000000001");
	private static final UUID RP_VIEW_ID = UUID.fromString("62000000-0000-0000-0000-000000000001");
	private static final UUID USER_ROLE_ID = UUID.fromString("63000000-0000-0000-0000-000000000001");

	private static final UUID NAV_DASH = UUID.fromString("50100000-0000-0000-0000-000000000001");
	private static final UUID NAV_USERS = UUID.fromString("50100000-0000-0000-0000-000000000002");
	private static final UUID NAV_SETTINGS = UUID.fromString("50100000-0000-0000-0000-000000000003");

	@Override
	public void execute(Database database) throws CustomChangeException {
		Instant now = Instant.now();
		Timestamp ts = Timestamp.from(now);

		try {
			Connection c = ((JdbcConnection) database.getConnection()).getUnderlyingConnection();
			c.setAutoCommit(false);
			try {
				insertTenant(c, ACME_TENANT_ID, "acme", "Acme Corp", ts);
				insertTpa(c, TPA_VIEW_ID, ACME_TENANT_ID, PRIV_USER_VIEW, ts);
				insertRole(c, ROLE_READER_ID, ACME_TENANT_ID, "Reader", ts);
				insertRolePrivilege(c, RP_VIEW_ID, ACME_TENANT_ID, ROLE_READER_ID, PRIV_USER_VIEW);
				insertMembership(c, MEMBERSHIP_ID, ACME_TENANT_ID, USER_ADMIN, ts);
				insertUserRole(c, USER_ROLE_ID, ACME_TENANT_ID, USER_ADMIN, ROLE_READER_ID, ts);
				insertNav(c, NAV_DASH, ACME_TENANT_ID, null, "/app", "nav.dashboard", 0, null, ts);
				insertNav(c, NAV_USERS, ACME_TENANT_ID, null, "/app/users", "nav.users", 10, "USER_VIEW", ts);
				insertNav(c, NAV_SETTINGS, ACME_TENANT_ID, null, "/app/settings", "nav.tenant_settings", 20,
						"TENANT_SETTINGS_EDIT", ts);
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

	private static void insertTenant(Connection c, UUID id, String handle, String name, Timestamp ts) throws Exception {
		try (PreparedStatement ps = c.prepareStatement(
				"INSERT INTO tenant (id, handle, name, created_at, updated_at) VALUES (?,?,?,?,?)")) {
			ps.setString(1, id.toString());
			ps.setString(2, handle);
			ps.setString(3, name);
			ps.setTimestamp(4, ts);
			ps.setTimestamp(5, ts);
			ps.executeUpdate();
		}
	}

	private static void insertTpa(Connection c, UUID id, UUID tenantId, UUID privId, Timestamp ts) throws Exception {
		try (PreparedStatement ps = c.prepareStatement(
				"INSERT INTO tenant_privilege_allowance (id, tenant_id, privilege_id, created_at, updated_at) VALUES (?,?,?,?,?)")) {
			ps.setString(1, id.toString());
			ps.setString(2, tenantId.toString());
			ps.setString(3, privId.toString());
			ps.setTimestamp(4, ts);
			ps.setTimestamp(5, ts);
			ps.executeUpdate();
		}
	}

	private static void insertRole(Connection c, UUID id, UUID tenantId, String name, Timestamp ts) throws Exception {
		try (PreparedStatement ps = c.prepareStatement(
				"INSERT INTO role (id, tenant_id, name, created_at, updated_at) VALUES (?,?,?,?,?)")) {
			ps.setString(1, id.toString());
			ps.setString(2, tenantId.toString());
			ps.setString(3, name);
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

	private static void insertMembership(Connection c, UUID id, UUID tenantId, UUID userId, Timestamp ts)
			throws Exception {
		try (PreparedStatement ps = c.prepareStatement(
				"INSERT INTO membership (id, tenant_id, user_id, created_at, updated_at) VALUES (?,?,?,?,?)")) {
			ps.setString(1, id.toString());
			ps.setString(2, tenantId.toString());
			ps.setString(3, userId.toString());
			ps.setTimestamp(4, ts);
			ps.setTimestamp(5, ts);
			ps.executeUpdate();
		}
	}

	private static void insertUserRole(Connection c, UUID id, UUID tenantId, UUID userId, UUID roleId, Timestamp ts)
			throws Exception {
		try (PreparedStatement ps = c.prepareStatement(
				"INSERT INTO user_role (id, tenant_id, user_id, role_id, created_at, updated_at) VALUES (?,?,?,?,?,?)")) {
			ps.setString(1, id.toString());
			ps.setString(2, tenantId.toString());
			ps.setString(3, userId.toString());
			ps.setString(4, roleId.toString());
			ps.setTimestamp(5, ts);
			ps.setTimestamp(6, ts);
			ps.executeUpdate();
		}
	}

	private static void insertNav(Connection c, UUID id, UUID tenantId, UUID parentId, String path, String labelKey,
			int sortOrder, String requiredPriv, Timestamp ts) throws Exception {
		try (PreparedStatement ps = c.prepareStatement(
				"INSERT INTO nav_menu_item (id, tenant_id, parent_id, path, label_key, sort_order, required_privilege_code, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?)")) {
			ps.setString(1, id.toString());
			ps.setString(2, tenantId.toString());
			if (parentId == null) {
				ps.setNull(3, Types.VARCHAR);
			}
			else {
				ps.setString(3, parentId.toString());
			}
			ps.setString(4, path);
			ps.setString(5, labelKey);
			ps.setInt(6, sortOrder);
			if (requiredPriv == null) {
				ps.setNull(7, Types.VARCHAR);
			}
			else {
				ps.setString(7, requiredPriv);
			}
			ps.setTimestamp(8, ts);
			ps.setTimestamp(9, ts);
			ps.executeUpdate();
		}
	}

	@Override
	public String getConfirmationMessage() {
		return "Acme second-tenant seed applied";
	}

	@Override
	public void setUp() throws SetupException {
		// no-op
	}

	@Override
	public void setFileOpener(ResourceAccessor resourceAccessor) {
		// no-op
	}

	@Override
	public ValidationErrors validate(Database database) {
		return new ValidationErrors();
	}
}
