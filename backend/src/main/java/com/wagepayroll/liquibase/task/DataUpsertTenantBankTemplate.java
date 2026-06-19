package com.wagepayroll.liquibase.task;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

/** Upserts {@code tenant_bank_template}. */
public class DataUpsertTenantBankTemplate extends CustomDataTaskChange {

	private String id;
	private String tenantId;
	private String companyId;
	private String platformBankTemplateId;
	private String countryCode;
	private String name;
	private String bankName;
	private String swiftBic;
	private String bankCode;
	private String accountNumberFormat;
	private String active;

	@Override
	public void handleUpdate() throws Exception {
		boolean activeBool = active == null || active.isBlank() || Boolean.parseBoolean(active.trim());
		try (PreparedStatement check = connection.prepareStatement(
				"SELECT COUNT(*) FROM tenant_bank_template WHERE id = ?")) {
			setData(check, 1, id);
			try (ResultSet rs = check.executeQuery()) {
				rs.next();
				if (rs.getInt(1) > 0) {
					try (PreparedStatement ps = connection.prepareStatement("""
							UPDATE tenant_bank_template SET
							  tenant_id = ?, company_id = ?, platform_bank_template_id = ?, country_code = ?,
							  name = ?, bank_name = ?, swift_bic = ?, bank_code = ?, account_number_format = ?,
							  active = ?, updated_at = ?
							WHERE id = ?
							""")) {
						int i = 1;
						setData(ps, i++, tenantId);
						setData(ps, i++, companyId);
						setData(ps, i++, platformBankTemplateId);
						setData(ps, i++, countryCode);
						setData(ps, i++, name);
						setData(ps, i++, bankName);
						setData(ps, i++, swiftBic);
						setData(ps, i++, bankCode);
						setData(ps, i++, accountNumberFormat);
						ps.setBoolean(i++, activeBool);
						setData(ps, i++, ts);
						setData(ps, i++, id);
						ps.executeUpdate();
					}
					return;
				}
			}
		}
		try (PreparedStatement ps = connection.prepareStatement("""
				INSERT INTO tenant_bank_template (
				  id, tenant_id, company_id, platform_bank_template_id, country_code,
				  name, bank_name, swift_bic, bank_code, account_number_format, active, created_at, updated_at
				) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
				""")) {
			int i = 1;
			setData(ps, i++, id);
			setData(ps, i++, tenantId);
			setData(ps, i++, companyId);
			setData(ps, i++, platformBankTemplateId);
			setData(ps, i++, countryCode);
			setData(ps, i++, name);
			setData(ps, i++, bankName);
			setData(ps, i++, swiftBic);
			setData(ps, i++, bankCode);
			setData(ps, i++, accountNumberFormat);
			ps.setBoolean(i++, activeBool);
			setData(ps, i++, ts);
			setData(ps, i++, ts);
			ps.executeUpdate();
		}
	}

	public String getId() { return id; }
	public void setId(String id) { this.id = id; }
	public String getTenantId() { return tenantId; }
	public void setTenantId(String tenantId) { this.tenantId = tenantId; }
	public String getCompanyId() { return companyId; }
	public void setCompanyId(String companyId) { this.companyId = companyId; }
	public String getPlatformBankTemplateId() { return platformBankTemplateId; }
	public void setPlatformBankTemplateId(String platformBankTemplateId) { this.platformBankTemplateId = platformBankTemplateId; }
	public String getCountryCode() { return countryCode; }
	public void setCountryCode(String countryCode) { this.countryCode = countryCode; }
	public String getName() { return name; }
	public void setName(String name) { this.name = name; }
	public String getBankName() { return bankName; }
	public void setBankName(String bankName) { this.bankName = bankName; }
	public String getSwiftBic() { return swiftBic; }
	public void setSwiftBic(String swiftBic) { this.swiftBic = swiftBic; }
	public String getBankCode() { return bankCode; }
	public void setBankCode(String bankCode) { this.bankCode = bankCode; }
	public String getAccountNumberFormat() { return accountNumberFormat; }
	public void setAccountNumberFormat(String accountNumberFormat) { this.accountNumberFormat = accountNumberFormat; }
	public String getActive() { return active; }
	public void setActive(String active) { this.active = active; }
}
