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

/**
 * M5 payroll organization privileges.
 */
public class DataM5PayrollOrgPrivileges1 implements CustomTaskChange {

	private static final UUID TENANT_DEMO = UUID.fromString("10000000-0000-0000-0000-000000000001");
	private static final UUID ROLE_ADMIN = UUID.fromString("40000000-0000-0000-0000-000000000001");

	private static final UUID PRIV_COMPANY_VIEW = UUID.fromString("20000000-0000-0000-0000-000000000015");
	private static final UUID PRIV_COMPANY_MANAGE = UUID.fromString("20000000-0000-0000-0000-000000000016");
	private static final UUID PRIV_DEPARTMENT_VIEW = UUID.fromString("20000000-0000-0000-0000-000000000017");
	private static final UUID PRIV_DEPARTMENT_MANAGE = UUID.fromString("20000000-0000-0000-0000-000000000018");
	private static final UUID PRIV_JOB_VIEW = UUID.fromString("20000000-0000-0000-0000-000000000019");
	private static final UUID PRIV_JOB_MANAGE = UUID.fromString("20000000-0000-0000-0000-000000000020");
	private static final UUID PRIV_EMPLOYEE_GROUP_VIEW = UUID.fromString("20000000-0000-0000-0000-000000000021");
	private static final UUID PRIV_EMPLOYEE_GROUP_MANAGE = UUID.fromString("20000000-0000-0000-0000-000000000022");
	private static final UUID PRIV_EMPLOYEE_VIEW = UUID.fromString("20000000-0000-0000-0000-000000000023");
	private static final UUID PRIV_EMPLOYEE_MANAGE = UUID.fromString("20000000-0000-0000-0000-000000000024");

	@Override
	public void execute(Database database) throws CustomChangeException {
		Instant now = Instant.now();
		Timestamp ts = Timestamp.from(now);
		try {
			Connection c = ((JdbcConnection) database.getConnection()).getUnderlyingConnection();
			c.setAutoCommit(false);
			try {
				upsertPrivilege(c, PRIV_COMPANY_VIEW, "COMPANY_VIEW", "View payroll companies", ts);
				upsertPrivilege(c, PRIV_COMPANY_MANAGE, "COMPANY_MANAGE", "Create and manage payroll companies", ts);
				upsertPrivilege(c, PRIV_DEPARTMENT_VIEW, "DEPARTMENT_VIEW", "View company departments", ts);
				upsertPrivilege(c, PRIV_DEPARTMENT_MANAGE, "DEPARTMENT_MANAGE", "Create and manage company departments", ts);
				upsertPrivilege(c, PRIV_JOB_VIEW, "JOB_VIEW", "View company jobs", ts);
				upsertPrivilege(c, PRIV_JOB_MANAGE, "JOB_MANAGE", "Create and manage company jobs", ts);
				upsertPrivilege(c, PRIV_EMPLOYEE_GROUP_VIEW, "EMPLOYEE_GROUP_VIEW", "View employee groups", ts);
				upsertPrivilege(c, PRIV_EMPLOYEE_GROUP_MANAGE, "EMPLOYEE_GROUP_MANAGE", "Create and manage employee groups", ts);
				upsertPrivilege(c, PRIV_EMPLOYEE_VIEW, "EMPLOYEE_VIEW", "View employees", ts);
				upsertPrivilege(c, PRIV_EMPLOYEE_MANAGE, "EMPLOYEE_MANAGE", "Create and manage employees", ts);

				insertRolePrivilegeIfMissing(c, TENANT_DEMO, ROLE_ADMIN, PRIV_COMPANY_VIEW);
				insertRolePrivilegeIfMissing(c, TENANT_DEMO, ROLE_ADMIN, PRIV_COMPANY_MANAGE);
				insertRolePrivilegeIfMissing(c, TENANT_DEMO, ROLE_ADMIN, PRIV_DEPARTMENT_VIEW);
				insertRolePrivilegeIfMissing(c, TENANT_DEMO, ROLE_ADMIN, PRIV_DEPARTMENT_MANAGE);
				insertRolePrivilegeIfMissing(c, TENANT_DEMO, ROLE_ADMIN, PRIV_JOB_VIEW);
				insertRolePrivilegeIfMissing(c, TENANT_DEMO, ROLE_ADMIN, PRIV_JOB_MANAGE);
				insertRolePrivilegeIfMissing(c, TENANT_DEMO, ROLE_ADMIN, PRIV_EMPLOYEE_GROUP_VIEW);
				insertRolePrivilegeIfMissing(c, TENANT_DEMO, ROLE_ADMIN, PRIV_EMPLOYEE_GROUP_MANAGE);
				insertRolePrivilegeIfMissing(c, TENANT_DEMO, ROLE_ADMIN, PRIV_EMPLOYEE_VIEW);
				insertRolePrivilegeIfMissing(c, TENANT_DEMO, ROLE_ADMIN, PRIV_EMPLOYEE_MANAGE);

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

	@Override
	public String getConfirmationMessage() {
		return "M5 payroll organization privileges seeded";
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
