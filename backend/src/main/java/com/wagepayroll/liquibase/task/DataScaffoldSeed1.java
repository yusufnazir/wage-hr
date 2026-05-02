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

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Scaffold seed: privileges, demo tenant, roles, memberships, and test users (DML via CustomTaskChange per
 * {@code docs/guides/LIQUIBASE-RULES.md}).
 */
public class DataScaffoldSeed1 implements CustomTaskChange {

	private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
	private static final UUID PRIV_USER_VIEW = UUID.fromString("20000000-0000-0000-0000-000000000001");
	private static final UUID PRIV_USER_EDIT = UUID.fromString("20000000-0000-0000-0000-000000000002");
	private static final UUID PRIV_TENANT_SETTINGS = UUID.fromString("20000000-0000-0000-0000-000000000003");

	private static final UUID USER_ADMIN = UUID.fromString("30000000-0000-0000-0000-000000000001");
	private static final UUID USER_VIEWER = UUID.fromString("30000000-0000-0000-0000-000000000002");
	private static final UUID USER_NOCODE = UUID.fromString("30000000-0000-0000-0000-000000000003");

	private static final UUID ROLE_ADMIN = UUID.fromString("40000000-0000-0000-0000-000000000001");
	private static final UUID ROLE_VIEWER = UUID.fromString("40000000-0000-0000-0000-000000000002");

	@Override
	public void execute(Database database) throws CustomChangeException {
		BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();
		String passwordHash = bcrypt.encode("ChangeMe!1");
		Instant now = Instant.now();
		Timestamp ts = Timestamp.from(now);

		try {
			Connection c = ((JdbcConnection) database.getConnection()).getUnderlyingConnection();
			c.setAutoCommit(false);
			try {
				insertPrivilege(c, PRIV_USER_VIEW, "USER_VIEW", "View users", ts);
				insertPrivilege(c, PRIV_USER_EDIT, "USER_EDIT", "Edit users", ts);
				insertPrivilege(c, PRIV_TENANT_SETTINGS, "TENANT_SETTINGS_EDIT", "Edit tenant settings", ts);

				insertTenant(c, TENANT_ID, "demo", "Demo tenant", ts);

				insertUser(c, USER_ADMIN, "admin@demo.lvh.me", passwordHash, ts);
				insertUser(c, USER_VIEWER, "viewer@demo.lvh.me", passwordHash, ts);
				insertUser(c, USER_NOCODE, "nocode@demo.lvh.me", passwordHash, ts);

				insertRole(c, ROLE_ADMIN, TENANT_ID, "Admin", ts);
				insertRole(c, ROLE_VIEWER, TENANT_ID, "Viewer", ts);

				insertRolePrivilege(c, UUID.randomUUID(), TENANT_ID, ROLE_ADMIN, PRIV_USER_VIEW);
				insertRolePrivilege(c, UUID.randomUUID(), TENANT_ID, ROLE_ADMIN, PRIV_USER_EDIT);
				insertRolePrivilege(c, UUID.randomUUID(), TENANT_ID, ROLE_ADMIN, PRIV_TENANT_SETTINGS);
				insertRolePrivilege(c, UUID.randomUUID(), TENANT_ID, ROLE_VIEWER, PRIV_USER_VIEW);

				insertMembership(c, UUID.randomUUID(), TENANT_ID, USER_ADMIN, ts);
				insertMembership(c, UUID.randomUUID(), TENANT_ID, USER_VIEWER, ts);
				insertMembership(c, UUID.randomUUID(), TENANT_ID, USER_NOCODE, ts);

				insertUserRole(c, UUID.randomUUID(), TENANT_ID, USER_ADMIN, ROLE_ADMIN, ts);
				insertUserRole(c, UUID.randomUUID(), TENANT_ID, USER_VIEWER, ROLE_VIEWER, ts);

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

	private static void insertUser(Connection c, UUID id, String email, String hash, Timestamp ts) throws Exception {
		try (PreparedStatement ps = c.prepareStatement(
				"INSERT INTO user_account (id, email, password_hash, created_at, updated_at) VALUES (?,?,?,?,?)")) {
			ps.setString(1, id.toString());
			ps.setString(2, email);
			ps.setString(3, hash);
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

	private static void insertMembership(Connection c, UUID id, UUID tenantId, UUID userId, Timestamp ts) throws Exception {
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

	@Override
	public String getConfirmationMessage() {
		return "Scaffold seed data applied";
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
