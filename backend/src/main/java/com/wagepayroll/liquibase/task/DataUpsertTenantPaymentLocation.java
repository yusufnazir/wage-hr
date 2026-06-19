package com.wagepayroll.liquibase.task;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

/** Upserts {@code tenant_payment_location}. */
public class DataUpsertTenantPaymentLocation extends CustomDataTaskChange {

	private String id;
	private String tenantId;
	private String companyId;
	private String name;
	private String paymentType;
	private String currency;
	private String bankTemplateId;
	/** When set, resolves {@code tenant_bank_template.id} for the tenant/company (used after catalog copy). */
	private String platformBankTemplateId;
	private String accountNumber;
	private String active;

	@Override
	public void handleUpdate() throws Exception {
		String resolvedBankTemplateId = resolveBankTemplateId();
		boolean activeBool = active == null || active.isBlank() || Boolean.parseBoolean(active.trim());
		try (PreparedStatement check = connection.prepareStatement(
				"SELECT COUNT(*) FROM tenant_payment_location WHERE id = ?")) {
			setData(check, 1, id);
			try (ResultSet rs = check.executeQuery()) {
				rs.next();
				if (rs.getInt(1) > 0) {
					try (PreparedStatement ps = connection.prepareStatement("""
							UPDATE tenant_payment_location SET
							  tenant_id = ?, company_id = ?, name = ?, payment_type = ?, currency = ?,
							  bank_template_id = ?, account_number = ?, active = ?, updated_at = ?
							WHERE id = ?
							""")) {
						int i = 1;
						setData(ps, i++, tenantId);
						setData(ps, i++, companyId);
						setData(ps, i++, name);
						setData(ps, i++, paymentType);
						setData(ps, i++, currency);
						setData(ps, i++, resolvedBankTemplateId);
						setData(ps, i++, accountNumber);
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
				INSERT INTO tenant_payment_location (
				  id, tenant_id, company_id, name, payment_type, currency, bank_template_id, account_number, active, created_at, updated_at
				) VALUES (?,?,?,?,?,?,?,?,?,?,?)
				""")) {
			int i = 1;
			setData(ps, i++, id);
			setData(ps, i++, tenantId);
			setData(ps, i++, companyId);
			setData(ps, i++, name);
			setData(ps, i++, paymentType);
			setData(ps, i++, currency);
			setData(ps, i++, resolvedBankTemplateId);
			setData(ps, i++, accountNumber);
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
	public String getName() { return name; }
	public void setName(String name) { this.name = name; }
	public String getPaymentType() { return paymentType; }
	public void setPaymentType(String paymentType) { this.paymentType = paymentType; }
	public String getCurrency() { return currency; }
	public void setCurrency(String currency) { this.currency = currency; }
	private String resolveBankTemplateId() throws Exception {
		if (bankTemplateId != null && !bankTemplateId.isBlank()) {
			return bankTemplateId;
		}
		if (platformBankTemplateId == null || platformBankTemplateId.isBlank()) {
			return null;
		}
		try (PreparedStatement ps = connection.prepareStatement("""
				SELECT id FROM tenant_bank_template
				WHERE tenant_id = ? AND company_id = ? AND platform_bank_template_id = ?
				""")) {
			ps.setString(1, tenantId);
			ps.setString(2, companyId);
			ps.setString(3, platformBankTemplateId);
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) {
					throw new IllegalStateException(
							"No tenant bank template for platformBankTemplateId=" + platformBankTemplateId
									+ " tenantId=" + tenantId + " companyId=" + companyId);
				}
				return rs.getString("id");
			}
		}
	}

	public String getBankTemplateId() { return bankTemplateId; }
	public void setBankTemplateId(String bankTemplateId) { this.bankTemplateId = bankTemplateId; }
	public String getPlatformBankTemplateId() { return platformBankTemplateId; }
	public void setPlatformBankTemplateId(String platformBankTemplateId) {
		this.platformBankTemplateId = platformBankTemplateId;
	}
	public String getAccountNumber() { return accountNumber; }
	public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
	public String getActive() { return active; }
	public void setActive(String active) { this.active = active; }
}
