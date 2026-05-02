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
 * M2: global privilege {@code USER_INVITE} and demo Admin role grant.
 */
public class DataM2UserInvitePrivilege1 implements CustomTaskChange {

	private static final UUID TENANT_DEMO = UUID.fromString("10000000-0000-0000-0000-000000000001");
	private static final UUID ROLE_ADMIN = UUID.fromString("40000000-0000-0000-0000-000000000001");
	private static final UUID PRIV_USER_INVITE = UUID.fromString("20000000-0000-0000-0000-000000000004");

	@Override
	public void execute(Database database) throws CustomChangeException {
		Instant now = Instant.now();
		Timestamp ts = Timestamp.from(now);
		try {
			Connection c = ((JdbcConnection) database.getConnection()).getUnderlyingConnection();
			c.setAutoCommit(false);
			try {
				try (PreparedStatement ps = c.prepareStatement(
						"INSERT INTO privilege (id, code, description, created_at, updated_at) VALUES (?,?,?,?,?)")) {
					ps.setString(1, PRIV_USER_INVITE.toString());
					ps.setString(2, "USER_INVITE");
					ps.setString(3, "Create and manage tenant invitations");
					ps.setTimestamp(4, ts);
					ps.setTimestamp(5, ts);
					ps.executeUpdate();
				}
				try (PreparedStatement ps = c.prepareStatement(
						"INSERT INTO role_privilege (id, tenant_id, role_id, privilege_id) VALUES (?,?,?,?)")) {
					ps.setString(1, UUID.randomUUID().toString());
					ps.setString(2, TENANT_DEMO.toString());
					ps.setString(3, ROLE_ADMIN.toString());
					ps.setString(4, PRIV_USER_INVITE.toString());
					ps.executeUpdate();
				}
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

	@Override
	public String getConfirmationMessage() {
		return "M2 USER_INVITE privilege seeded for demo tenant";
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
