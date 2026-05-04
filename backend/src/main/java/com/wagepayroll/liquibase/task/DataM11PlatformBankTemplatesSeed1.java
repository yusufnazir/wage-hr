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
 * Idempotent seed: Suriname (SR) platform bank templates per product module.
 */
public class DataM11PlatformBankTemplatesSeed1 implements CustomTaskChange {

	private static final UUID ID_HAKRIN = UUID.fromString("2f110000-0000-4000-8000-000000000001");
	private static final UUID ID_DSB = UUID.fromString("2f110000-0000-4000-8000-000000000002");
	private static final UUID ID_FINA = UUID.fromString("2f110000-0000-4000-8000-000000000003");
	private static final UUID ID_RBC = UUID.fromString("2f110000-0000-4000-8000-000000000004");

	@Override
	public void execute(Database database) throws CustomChangeException {
		Instant now = Instant.now();
		Timestamp ts = Timestamp.from(now);
		try {
			Connection c = ((JdbcConnection) database.getConnection()).getUnderlyingConnection();
			c.setAutoCommit(false);
			try {
				int existing;
				try (PreparedStatement ps = c.prepareStatement(
						"SELECT COUNT(*) FROM platform_bank_template WHERE country_code = 'SR'")) {
					try (ResultSet rs = ps.executeQuery()) {
						rs.next();
						existing = rs.getInt(1);
					}
				}
				if (existing >= 4) {
					c.commit();
					return;
				}

				insertTemplate(c, ID_HAKRIN, "SR", "Standard Bank Transfer — Hakrinbank", "Hakrinbank N.V.", "HAKRSR22",
						null, null, "SRD", true, ts);
				insertTemplate(c, ID_DSB, "SR", "Standard Bank Transfer — DSB Bank", "De Surinaamsche Bank N.V.",
						"DSBLSR22", null, null, "SRD", true, ts);
				insertTemplate(c, ID_FINA, "SR", "Standard Bank Transfer — Finabank", "Finabank N.V.", "FINLSRSS",
						null, null, "SRD", true, ts);
				insertTemplate(c, ID_RBC, "SR", "Standard Bank Transfer — RBC Royal Bank",
						"RBC Royal Bank (Suriname) N.V.", "ROYCSR22", null, null, "SRD", true, ts);

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

	private static void insertTemplate(Connection c, UUID id, String countryCode, String name, String bankName,
			String swiftBic, String bankCode, String accountNumberFormat, String currencyCode, boolean active,
			Timestamp ts) throws Exception {
		try (PreparedStatement ps = c.prepareStatement("""
				INSERT INTO platform_bank_template (
				  id, country_code, name, bank_name, swift_bic, bank_code, account_number_format, currency_code, active, created_at, updated_at
				) VALUES (?,?,?,?,?,?,?,?,?,?,?)
				""")) {
			ps.setString(1, id.toString());
			ps.setString(2, countryCode);
			ps.setString(3, name);
			ps.setString(4, bankName);
			ps.setString(5, swiftBic);
			ps.setString(6, bankCode);
			ps.setString(7, accountNumberFormat);
			ps.setString(8, currencyCode);
			ps.setBoolean(9, active);
			ps.setTimestamp(10, ts);
			ps.setTimestamp(11, ts);
			ps.executeUpdate();
		}
	}

	@Override
	public String getConfirmationMessage() {
		return "M11 platform bank templates SR seed applied";
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
