package com.wagepayroll.liquibase.task;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
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
 * Backfills tenant currency visibility for existing tenants created before M7 template updates.
 */
public class DataM7BackfillTenantCurrencyVisibility1 implements CustomTaskChange {

	private static final String TENANT_CURRENCIES_PATH = "/app/tenant-currencies";

	@Override
	public void execute(Database database) throws CustomChangeException {
		Timestamp ts = Timestamp.from(Instant.now());
		try {
			Connection c = ((JdbcConnection) database.getConnection()).getUnderlyingConnection();
			c.setAutoCommit(false);
			try {
				UUID privView = resolvePrivilegeId(c, "TENANT_CURRENCY_VIEW");
				UUID privEdit = resolvePrivilegeId(c, "TENANT_CURRENCY_EDIT");
				for (UUID tenantId : allTenantIds(c)) {
					insertTenantPrivilegeAllowanceIfMissing(c, tenantId, privView, ts);
					insertTenantPrivilegeAllowanceIfMissing(c, tenantId, privEdit, ts);
					insertTenantCurrenciesNavIfMissing(c, tenantId, ts);
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

	private static List<UUID> allTenantIds(Connection c) throws Exception {
		List<UUID> out = new ArrayList<>();
		try (PreparedStatement ps = c.prepareStatement("SELECT id FROM tenant")) {
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					out.add(UUID.fromString(rs.getString(1)));
				}
			}
		}
		return out;
	}

	private static void insertTenantPrivilegeAllowanceIfMissing(Connection c, UUID tenantId, UUID privilegeId, Timestamp ts)
			throws Exception {
		try (PreparedStatement check = c.prepareStatement(
				"SELECT count(*) FROM tenant_privilege_allowance WHERE tenant_id = ? AND privilege_id = ?")) {
			check.setString(1, tenantId.toString());
			check.setString(2, privilegeId.toString());
			try (ResultSet rs = check.executeQuery()) {
				rs.next();
				if (rs.getLong(1) > 0) {
					return;
				}
			}
		}
		try (PreparedStatement insert = c.prepareStatement(
				"INSERT INTO tenant_privilege_allowance (id, tenant_id, privilege_id, created_at, updated_at) VALUES (?,?,?,?,?)")) {
			insert.setString(1, UUID.randomUUID().toString());
			insert.setString(2, tenantId.toString());
			insert.setString(3, privilegeId.toString());
			insert.setTimestamp(4, ts);
			insert.setTimestamp(5, ts);
			insert.executeUpdate();
		}
	}

	private static void insertTenantCurrenciesNavIfMissing(Connection c, UUID tenantId, Timestamp ts) throws Exception {
		try (PreparedStatement check = c
				.prepareStatement("SELECT count(*) FROM nav_menu_item WHERE tenant_id = ? AND path = ?")) {
			check.setString(1, tenantId.toString());
			check.setString(2, TENANT_CURRENCIES_PATH);
			try (ResultSet rs = check.executeQuery()) {
				rs.next();
				if (rs.getLong(1) > 0) {
					return;
				}
			}
		}

		try (PreparedStatement insert = c.prepareStatement(
				"INSERT INTO nav_menu_item (id, tenant_id, parent_id, path, label_key, sort_order, required_privilege_code, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?)")) {
			insert.setString(1, UUID.randomUUID().toString());
			insert.setString(2, tenantId.toString());
			insert.setNull(3, Types.VARCHAR);
			insert.setString(4, TENANT_CURRENCIES_PATH);
			insert.setString(5, "nav.tenant_currencies");
			insert.setInt(6, 16);
			insert.setString(7, "TENANT_CURRENCY_VIEW");
			insert.setTimestamp(8, ts);
			insert.setTimestamp(9, ts);
			insert.executeUpdate();
		}
	}

	@Override
	public String getConfirmationMessage() {
		return "M7 tenant currency visibility backfill applied";
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
