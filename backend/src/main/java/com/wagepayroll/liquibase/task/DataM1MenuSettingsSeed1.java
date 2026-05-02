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
 * M1 seed: demo navigation rows, tenant + platform settings, platform superadmin flag for scaffold admin user.
 */
public class DataM1MenuSettingsSeed1 implements CustomTaskChange {

	private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
	private static final UUID USER_ADMIN = UUID.fromString("30000000-0000-0000-0000-000000000001");

	private static final UUID PLATFORM_SETTING_ID = UUID.fromString("51000000-0000-0000-0000-000000000001");
	private static final UUID TENANT_SETTING_ID = UUID.fromString("52000000-0000-0000-0000-000000000001");
	private static final UUID NAV_DASH = UUID.fromString("50000000-0000-0000-0000-000000000001");
	private static final UUID NAV_USERS = UUID.fromString("50000000-0000-0000-0000-000000000002");
	private static final UUID NAV_SETTINGS = UUID.fromString("50000000-0000-0000-0000-000000000003");
	private static final UUID NAV_DOCUMENTS = UUID.fromString("50000000-0000-0000-0000-000000000004");
	private static final UUID NAV_ROLE_ADMIN = UUID.fromString("50000000-0000-0000-0000-000000000005");
	private static final UUID NAV_TENANT_CURRENCIES = UUID.fromString("50000000-0000-0000-0000-000000000006");

	@Override
	public void execute(Database database) throws CustomChangeException {
		Instant now = Instant.now();
		Timestamp ts = Timestamp.from(now);

		try {
			Connection c = ((JdbcConnection) database.getConnection()).getUnderlyingConnection();
			c.setAutoCommit(false);
			try {
				try (PreparedStatement ps = c.prepareStatement(
						"UPDATE user_account SET platform_superadmin = ?, updated_at = ? WHERE id = ?")) {
					ps.setBoolean(1, true);
					ps.setTimestamp(2, ts);
					ps.setString(3, USER_ADMIN.toString());
					ps.executeUpdate();
				}

				// Column `key` must be quoted for MariaDB/MySQL (reserved word) in raw JDBC.
				try (PreparedStatement ps = c.prepareStatement(
						"INSERT INTO platform_setting (id, `key`, value_text, created_at, updated_at) VALUES (?,?,?,?,?)")) {
					ps.setString(1, PLATFORM_SETTING_ID.toString());
					ps.setString(2, "platform.product_name");
					ps.setString(3, "Wage Payroll");
					ps.setTimestamp(4, ts);
					ps.setTimestamp(5, ts);
					ps.executeUpdate();
				}

				try (PreparedStatement ps = c.prepareStatement(
						"INSERT INTO tenant_setting (id, tenant_id, `key`, value_text, created_at, updated_at) VALUES (?,?,?,?,?,?)")) {
					ps.setString(1, TENANT_SETTING_ID.toString());
					ps.setString(2, TENANT_ID.toString());
					ps.setString(3, "tenant.demo_flag");
					ps.setString(4, "1");
					ps.setTimestamp(5, ts);
					ps.setTimestamp(6, ts);
					ps.executeUpdate();
				}

				insertNav(c, NAV_DASH, "/app", "nav.dashboard", 0, null, null, ts);
				insertNav(c, NAV_USERS, "/app/users", "nav.users", 10, "USER_VIEW", null, ts);
				insertNav(c, NAV_TENANT_CURRENCIES, "/app/tenant-currencies", "nav.tenant_currencies", 16,
						"TENANT_CURRENCY_VIEW", null, ts);
				insertNav(c, NAV_SETTINGS, "/app/settings", "nav.tenant_settings", 20, "TENANT_SETTINGS_EDIT", null,
						ts);
				insertNav(c, NAV_DOCUMENTS, "/app/documents", "nav.documents", 30, "DOCUMENT_VIEW", null, ts);
				insertNav(c, NAV_ROLE_ADMIN, "/app/roles", "nav.role_admin", 35, "ROLE_VIEW", null, ts);

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

	private static void insertNav(Connection c, UUID id, String path, String labelKey, int sortOrder,
			String requiredPriv, String requiredPlanFeature, Timestamp ts) throws Exception {
		try (PreparedStatement ps = c.prepareStatement(
				"INSERT INTO nav_menu_item (id, parent_id, path, label_key, sort_order, required_privilege_code, required_plan_feature_code, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?)")) {
			ps.setString(1, id.toString());
			ps.setObject(2, null);
			ps.setString(3, path);
			ps.setString(4, labelKey);
			ps.setInt(5, sortOrder);
			if (requiredPriv == null) {
				ps.setObject(6, null);
			}
			else {
				ps.setString(6, requiredPriv);
			}
			if (requiredPlanFeature == null) {
				ps.setObject(7, null);
			}
			else {
				ps.setString(7, requiredPlanFeature);
			}
			ps.setTimestamp(8, ts);
			ps.setTimestamp(9, ts);
			ps.executeUpdate();
		}
	}

	@Override
	public String getConfirmationMessage() {
		return "M1 menu and settings seed applied";
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
