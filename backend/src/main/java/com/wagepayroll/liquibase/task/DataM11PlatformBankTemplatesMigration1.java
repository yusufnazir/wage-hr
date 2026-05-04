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
 * Fixes the 4 existing SR bank templates with correct SWIFT BICs and adds the remaining 7 Suriname banks.
 * Preserves original row IDs (001–004) so existing tenant_bank_template FK references are not broken.
 */
public class DataM11PlatformBankTemplatesMigration1 implements CustomTaskChange {

	// All 11 Suriname bank template IDs (001–011)
	private static final UUID ID_HAKRIN           = UUID.fromString("2f110000-0000-4000-8000-000000000001");
	private static final UUID ID_DSB              = UUID.fromString("2f110000-0000-4000-8000-000000000002");
	private static final UUID ID_FINABANK         = UUID.fromString("2f110000-0000-4000-8000-000000000003");
	private static final UUID ID_REPUBLIC         = UUID.fromString("2f110000-0000-4000-8000-000000000004");
	private static final UUID ID_CENTRALE_BANK    = UUID.fromString("2f110000-0000-4000-8000-000000000005");
	private static final UUID ID_GODO             = UUID.fromString("2f110000-0000-4000-8000-000000000006");
	private static final UUID ID_FINATRUST        = UUID.fromString("2f110000-0000-4000-8000-000000000007");
	private static final UUID ID_SOUTHERN         = UUID.fromString("2f110000-0000-4000-8000-000000000008");
	private static final UUID ID_SURICHANGE       = UUID.fromString("2f110000-0000-4000-8000-000000000009");
	private static final UUID ID_POSTSPAARBANK    = UUID.fromString("2f110000-0000-4000-8000-000000000010");
	private static final UUID ID_VOLKSCREDIETBANK = UUID.fromString("2f110000-0000-4000-8000-000000000011");

	@Override
	public void execute(Database database) throws CustomChangeException {
		Instant now = Instant.now();
		Timestamp ts = Timestamp.from(now);
		try {
			Connection c = ((JdbcConnection) database.getConnection()).getUnderlyingConnection();
			c.setAutoCommit(false);
			try {
				upsert(c, ID_HAKRIN,           "SR", "Standard Bank Transfer — Hakrinbank",                    "Hakrinbank N.V.",                               "HAKRSRPA", "SRD", ts);
				upsert(c, ID_DSB,              "SR", "Standard Bank Transfer — DSB Bank",                      "De Surinaamsche Bank N.V.",                      "SURBSRPA", "SRD", ts);
				upsert(c, ID_FINABANK,         "SR", "Standard Bank Transfer — Finabank",                      "Finabank N.V.",                                  "FBNASRPA", "SRD", ts);
				upsert(c, ID_REPUBLIC,         "SR", "Standard Bank Transfer — Republic Bank",                 "Republic Bank (Suriname) N.V.",                  "RBNKSRPA", "SRD", ts);
				upsert(c, ID_CENTRALE_BANK,    "SR", "Standard Bank Transfer — Centrale Bank van Suriname",    "Centrale Bank van Suriname",                     "CBVSSRPA", "SRD", ts);
				upsert(c, ID_GODO,             "SR", "Standard Bank Transfer — GODO",                          "Cooperatieve Spaar- en Kredietbank GODO U.A.",   "GODOSRPA", "SRD", ts);
				upsert(c, ID_FINATRUST,        "SR", "Standard Bank Transfer — Finatrust",                     "Finatrust, De Trustbank N.V.",                   "ICTBSRPA", "SRD", ts);
				upsert(c, ID_SOUTHERN,         "SR", "Standard Bank Transfer — Southern Commercial Bank",      "Southern Commercial Bank N.V.",                  "SOUOSRPP", "SRD", ts);
				upsert(c, ID_SURICHANGE,       "SR", "Standard Bank Transfer — Surichange Bank",               "Surichange Bank N.V.",                           "SURCSRPA", "SRD", ts);
				upsert(c, ID_POSTSPAARBANK,    "SR", "Standard Bank Transfer — Surinaamse Postspaarbank",      "Surinaamse Postspaarbank",                       "SDPOSRPA", "SRD", ts);
				upsert(c, ID_VOLKSCREDIETBANK, "SR", "Standard Bank Transfer — Surinaamse Volkscredietbank",   "Surinaamse Volkscredietbank",                    "VCBSSRPA", "SRD", ts);

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

	private static void upsert(Connection c, UUID id, String countryCode, String name, String bankName,
			String swiftBic, String currencyCode, Timestamp ts) throws Exception {
		try (PreparedStatement check = c.prepareStatement(
				"SELECT COUNT(*) FROM platform_bank_template WHERE id = ?")) {
			check.setString(1, id.toString());
			try (ResultSet rs = check.executeQuery()) {
				rs.next();
				if (rs.getInt(1) > 0) {
					try (PreparedStatement ps = c.prepareStatement("""
							UPDATE platform_bank_template
							SET name = ?, bank_name = ?, swift_bic = ?, currency_code = ?, updated_at = ?
							WHERE id = ?
							""")) {
						ps.setString(1, name);
						ps.setString(2, bankName);
						ps.setString(3, swiftBic);
						ps.setString(4, currencyCode);
						ps.setTimestamp(5, ts);
						ps.setString(6, id.toString());
						ps.executeUpdate();
					}
					return;
				}
			}
		}
		try (PreparedStatement ps = c.prepareStatement("""
				INSERT INTO platform_bank_template (
				  id, country_code, name, bank_name, swift_bic, bank_code, account_number_format, currency_code, active, created_at, updated_at
				) VALUES (?,?,?,?,?,NULL,NULL,?,true,?,?)
				""")) {
			ps.setString(1, id.toString());
			ps.setString(2, countryCode);
			ps.setString(3, name);
			ps.setString(4, bankName);
			ps.setString(5, swiftBic);
			ps.setString(6, currencyCode);
			ps.setTimestamp(7, ts);
			ps.setTimestamp(8, ts);
			ps.executeUpdate();
		}
	}

	@Override
	public String getConfirmationMessage() {
		return "M11 platform bank templates SR migration applied (fixed SWIFT BICs + 7 new banks)";
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
