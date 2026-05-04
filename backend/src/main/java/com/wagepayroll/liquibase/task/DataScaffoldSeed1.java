package com.wagepayroll.liquibase.task;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
				upsertPrivilege(c, PRIV_USER_VIEW, "USER_VIEW", "View users", ts);
				upsertPrivilege(c, PRIV_USER_EDIT, "USER_EDIT", "Edit users", ts);
				upsertPrivilege(c, PRIV_TENANT_SETTINGS, "TENANT_SETTINGS_EDIT", "Edit tenant settings", ts);

				upsertTenant(c, TENANT_ID, "demo", "Demo tenant", ts);

				upsertUser(c, USER_ADMIN, "admin@demo.lvh.me", passwordHash, ts);
				upsertUser(c, USER_VIEWER, "viewer@demo.lvh.me", passwordHash, ts);
				upsertUser(c, USER_NOCODE, "nocode@demo.lvh.me", passwordHash, ts);

				upsertRole(c, ROLE_ADMIN, TENANT_ID, "Admin", ts);
				upsertRole(c, ROLE_VIEWER, TENANT_ID, "Viewer", ts);

				insertRolePrivilegeIfMissing(c, TENANT_ID, ROLE_ADMIN, PRIV_USER_VIEW);
				insertRolePrivilegeIfMissing(c, TENANT_ID, ROLE_ADMIN, PRIV_USER_EDIT);
				insertRolePrivilegeIfMissing(c, TENANT_ID, ROLE_ADMIN, PRIV_TENANT_SETTINGS);
				insertRolePrivilegeIfMissing(c, TENANT_ID, ROLE_VIEWER, PRIV_USER_VIEW);

				insertMembershipIfMissing(c, TENANT_ID, USER_ADMIN, ts);
				insertMembershipIfMissing(c, TENANT_ID, USER_VIEWER, ts);
				insertMembershipIfMissing(c, TENANT_ID, USER_NOCODE, ts);

				insertUserRoleIfMissing(c, TENANT_ID, USER_ADMIN, ROLE_ADMIN, ts);
				insertUserRoleIfMissing(c, TENANT_ID, USER_VIEWER, ROLE_VIEWER, ts);

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

	private static void upsertPrivilege(Connection c, UUID id, String code, String desc, Timestamp ts) throws Exception {
		try (PreparedStatement check = c.prepareStatement("SELECT COUNT(*) FROM privilege WHERE id = ?")) {
			check.setString(1, id.toString());
			try (ResultSet rs = check.executeQuery()) {
				rs.next();
				if (rs.getInt(1) > 0) {
					try (PreparedStatement ps = c.prepareStatement(
							"UPDATE privilege SET code = ?, description = ?, updated_at = ? WHERE id = ?")) {
						ps.setString(1, code);
						ps.setString(2, desc);
						ps.setTimestamp(3, ts);
						ps.setString(4, id.toString());
						ps.executeUpdate();
					}
					return;
				}
			}
		}
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

	private static void upsertTenant(Connection c, UUID id, String handle, String name, Timestamp ts) throws Exception {
		try (PreparedStatement check = c.prepareStatement("SELECT COUNT(*) FROM tenant WHERE id = ?")) {
			check.setString(1, id.toString());
			try (ResultSet rs = check.executeQuery()) {
				rs.next();
				if (rs.getInt(1) > 0) {
					try (PreparedStatement ps = c.prepareStatement(
							"UPDATE tenant SET handle = ?, name = ?, updated_at = ? WHERE id = ?")) {
						ps.setString(1, handle);
						ps.setString(2, name);
						ps.setTimestamp(3, ts);
						ps.setString(4, id.toString());
						ps.executeUpdate();
					}
					return;
				}
			}
		}
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

	private static void upsertUser(Connection c, UUID id, String email, String hash, Timestamp ts) throws Exception {
		try (PreparedStatement check = c.prepareStatement("SELECT COUNT(*) FROM user_account WHERE id = ?")) {
			check.setString(1, id.toString());
			try (ResultSet rs = check.executeQuery()) {
				rs.next();
				if (rs.getInt(1) > 0) {
					try (PreparedStatement ps = c.prepareStatement(
							"UPDATE user_account SET email = ?, password_hash = ?, updated_at = ? WHERE id = ?")) {
						ps.setString(1, email);
						ps.setString(2, hash);
						ps.setTimestamp(3, ts);
						ps.setString(4, id.toString());
						ps.executeUpdate();
					}
					return;
				}
			}
		}
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

	private static void upsertRole(Connection c, UUID id, UUID tenantId, String name, Timestamp ts) throws Exception {
		try (PreparedStatement check = c.prepareStatement("SELECT COUNT(*) FROM role WHERE id = ?")) {
			check.setString(1, id.toString());
			try (ResultSet rs = check.executeQuery()) {
				rs.next();
				if (rs.getInt(1) > 0) {
					try (PreparedStatement ps = c.prepareStatement(
							"UPDATE role SET name = ?, updated_at = ? WHERE id = ?")) {
						ps.setString(1, name);
						ps.setTimestamp(2, ts);
						ps.setString(3, id.toString());
						ps.executeUpdate();
					}
					return;
				}
			}
		}
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

	private static void insertRolePrivilegeIfMissing(Connection c, UUID tenantId, UUID roleId, UUID privId)
			throws Exception {
		try (PreparedStatement check = c.prepareStatement(
				"SELECT COUNT(*) FROM role_privilege WHERE tenant_id = ? AND role_id = ? AND privilege_id = ?")) {
			check.setString(1, tenantId.toString());
			check.setString(2, roleId.toString());
			check.setString(3, privId.toString());
			try (ResultSet rs = check.executeQuery()) {
				rs.next();
				if (rs.getInt(1) > 0) return;
			}
		}
		try (PreparedStatement ps = c.prepareStatement(
				"INSERT INTO role_privilege (id, tenant_id, role_id, privilege_id) VALUES (?,?,?,?)")) {
			ps.setString(1, UUID.randomUUID().toString());
			ps.setString(2, tenantId.toString());
			ps.setString(3, roleId.toString());
			ps.setString(4, privId.toString());
			ps.executeUpdate();
		}
	}

	private static void insertMembershipIfMissing(Connection c, UUID tenantId, UUID userId, Timestamp ts)
			throws Exception {
		try (PreparedStatement check = c.prepareStatement(
				"SELECT COUNT(*) FROM membership WHERE tenant_id = ? AND user_id = ?")) {
			check.setString(1, tenantId.toString());
			check.setString(2, userId.toString());
			try (ResultSet rs = check.executeQuery()) {
				rs.next();
				if (rs.getInt(1) > 0) return;
			}
		}
		try (PreparedStatement ps = c.prepareStatement(
				"INSERT INTO membership (id, tenant_id, user_id, created_at, updated_at) VALUES (?,?,?,?,?)")) {
			ps.setString(1, UUID.randomUUID().toString());
			ps.setString(2, tenantId.toString());
			ps.setString(3, userId.toString());
			ps.setTimestamp(4, ts);
			ps.setTimestamp(5, ts);
			ps.executeUpdate();
		}
	}

	private static void insertUserRoleIfMissing(Connection c, UUID tenantId, UUID userId, UUID roleId, Timestamp ts)
			throws Exception {
		try (PreparedStatement check = c.prepareStatement(
				"SELECT COUNT(*) FROM user_role WHERE tenant_id = ? AND user_id = ? AND role_id = ?")) {
			check.setString(1, tenantId.toString());
			check.setString(2, userId.toString());
			check.setString(3, roleId.toString());
			try (ResultSet rs = check.executeQuery()) {
				rs.next();
				if (rs.getInt(1) > 0) return;
			}
		}
		try (PreparedStatement ps = c.prepareStatement(
				"INSERT INTO user_role (id, tenant_id, user_id, role_id, created_at, updated_at) VALUES (?,?,?,?,?,?)")) {
			ps.setString(1, UUID.randomUUID().toString());
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
