package com.wagepayroll.liquibase.task;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Locale;
import java.util.UUID;

/**
 * Copies all active {@code platform_bank_template} rows for a payroll country into {@code tenant_bank_template}
 * for a company. Idempotent: skips platform templates already linked on the company.
 */
public class DataCopyTenantBankTemplatesFromPlatform extends CustomDataTaskChange {

	private String tenantId;
	private String companyId;
	private String payrollCountry;
	private String backfillAll;

	@Override
	public void handleUpdate() throws Exception {
		if ("true".equalsIgnoreCase(trimToEmpty(backfillAll))) {
			try (PreparedStatement companies = connection.prepareStatement("""
					SELECT tenant_id, id, payroll_country FROM tenant_company
					WHERE payroll_country IS NOT NULL AND TRIM(payroll_country) <> ''
					""")) {
				try (ResultSet rs = companies.executeQuery()) {
					while (rs.next()) {
						copyForCompany(rs.getString("tenant_id"), rs.getString("id"), rs.getString("payroll_country"));
					}
				}
			}
			return;
		}
		if (tenantId == null || tenantId.isBlank() || companyId == null || companyId.isBlank()
				|| payrollCountry == null || payrollCountry.isBlank()) {
			return;
		}
		copyForCompany(tenantId, companyId, payrollCountry);
	}

	private void copyForCompany(String tenantIdValue, String companyIdValue, String payrollCountryValue) throws Exception {
		String country = payrollCountryValue.trim().toUpperCase(Locale.ROOT);
		try (PreparedStatement sources = connection.prepareStatement("""
				SELECT id, country_code, name, bank_name, swift_bic, bank_code, account_number_format, active
				FROM platform_bank_template
				WHERE country_code = ? AND active = true
				ORDER BY name ASC
				""")) {
			sources.setString(1, country);
			try (ResultSet rs = sources.executeQuery()) {
				while (rs.next()) {
					String platformId = rs.getString("id");
					if (alreadyCopied(tenantIdValue, companyIdValue, platformId)) {
						continue;
					}
					insertCopy(tenantIdValue, companyIdValue, platformId, rs);
				}
			}
		}
	}

	private static String trimToEmpty(String value) {
		return value == null ? "" : value.trim();
	}

	private boolean alreadyCopied(String tenantIdValue, String companyIdValue, String platformBankTemplateId)
			throws Exception {
		try (PreparedStatement ps = connection.prepareStatement("""
				SELECT COUNT(*) FROM tenant_bank_template
				WHERE tenant_id = ? AND company_id = ? AND platform_bank_template_id = ?
				""")) {
			ps.setString(1, tenantIdValue);
			ps.setString(2, companyIdValue);
			ps.setString(3, platformBankTemplateId);
			try (ResultSet rs = ps.executeQuery()) {
				rs.next();
				return rs.getInt(1) > 0;
			}
		}
	}

	private void insertCopy(String tenantIdValue, String companyIdValue, String platformId, ResultSet platformRow)
			throws Exception {
		try (PreparedStatement ps = connection.prepareStatement("""
				INSERT INTO tenant_bank_template (
				  id, tenant_id, company_id, platform_bank_template_id, country_code,
				  name, bank_name, swift_bic, bank_code, account_number_format, active, created_at, updated_at
				) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
				""")) {
			int i = 1;
			setData(ps, i++, UUID.randomUUID().toString());
			setData(ps, i++, tenantIdValue);
			setData(ps, i++, companyIdValue);
			setData(ps, i++, platformId);
			setData(ps, i++, platformRow.getString("country_code"));
			setData(ps, i++, platformRow.getString("name"));
			setData(ps, i++, platformRow.getString("bank_name"));
			setData(ps, i++, platformRow.getString("swift_bic"));
			setData(ps, i++, platformRow.getString("bank_code"));
			setData(ps, i++, platformRow.getString("account_number_format"));
			ps.setBoolean(i++, platformRow.getBoolean("active"));
			setData(ps, i++, ts);
			setData(ps, i++, ts);
			ps.executeUpdate();
		}
	}

	public String getTenantId() {
		return tenantId;
	}

	public void setTenantId(String tenantId) {
		this.tenantId = tenantId;
	}

	public String getCompanyId() {
		return companyId;
	}

	public void setCompanyId(String companyId) {
		this.companyId = companyId;
	}

	public String getPayrollCountry() {
		return payrollCountry;
	}

	public void setPayrollCountry(String payrollCountry) {
		this.payrollCountry = payrollCountry;
	}

	public String getBackfillAll() {
		return backfillAll;
	}

	public void setBackfillAll(String backfillAll) {
		this.backfillAll = backfillAll;
	}
}
