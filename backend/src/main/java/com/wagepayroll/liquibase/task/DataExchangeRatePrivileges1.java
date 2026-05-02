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

public class DataExchangeRatePrivileges1 implements CustomTaskChange {

	private static final UUID TENANT_DEMO = UUID.fromString("10000000-0000-0000-0000-000000000001");
	private static final UUID ROLE_ADMIN = UUID.fromString("40000000-0000-0000-0000-000000000001");
	private static final UUID ROLE_VIEWER = UUID.fromString("40000000-0000-0000-0000-000000000002");


	private static final UUID PRIV_EXCHANGE_RATE_VIEW = UUID.fromString("20000000-0000-0000-0000-000000000013");
	private static final UUID PRIV_EXCHANGE_RATE_MANAGE = UUID.fromString("20000000-0000-0000-0000-000000000014");

	@Override
	public void execute(Database database) throws CustomChangeException {
		Instant now = Instant.now();
		Timestamp ts = Timestamp.from(now);
		try {
			Connection c = ((JdbcConnection) database.getConnection()).getUnderlyingConnection();
			c.setAutoCommit(false);
			try {
				insertPrivilege(c, PRIV_EXCHANGE_RATE_VIEW, "EXCHANGE_RATE_VIEW", "View tenant exchange rates", ts);
				insertPrivilege(c, PRIV_EXCHANGE_RATE_MANAGE, "EXCHANGE_RATE_MANAGE", "Manage tenant exchange rates", ts);

				insertRolePrivilege(c, UUID.randomUUID(), TENANT_DEMO, ROLE_ADMIN, PRIV_EXCHANGE_RATE_VIEW);
				insertRolePrivilege(c, UUID.randomUUID(), TENANT_DEMO, ROLE_ADMIN, PRIV_EXCHANGE_RATE_MANAGE);
				insertRolePrivilege(c, UUID.randomUUID(), TENANT_DEMO, ROLE_VIEWER, PRIV_EXCHANGE_RATE_VIEW);


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
		return "Exchange rate privileges seeded";
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
