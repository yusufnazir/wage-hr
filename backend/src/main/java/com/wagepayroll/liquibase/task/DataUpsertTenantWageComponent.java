package com.wagepayroll.liquibase.task;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

/** Upserts {@code tenant_wage_component}. */
public class DataUpsertTenantWageComponent extends CustomDataTaskChange {

	private String id;
	private String tenantId;
	private String companyId;
	private String platformTemplateId;
	private String code;
	private String name;
	private String description;
	private String componentType;
	private String category;
	private String netEffect;
	private String taxableWageTax;
	private String taxableSocialSecurity;
	private String taxablePension;
	private String taxableVacationReserve;
	private String calculationMethod;
	private String percentageBase;
	private String formulaExpression;
	private String defaultAmount;
	private String roundingStrategy;
	private String processingOrder;
	private String phase;
	private String maintainsBalance;
	private String balanceType;
	private String balanceDirection;
	private String counterComponentId;
	private String postingStrategy;
	private String printOnPayslip;
	private String auxiliary;
	private String applyInPayroll;
	private String recurrence;
	private String countryRuleKey;
	private String platformCountryTaxRuleId;
	private String debitTenantLedgerId;
	private String creditTenantLedgerId;
	private String active;

	private static boolean parseBool(String raw, boolean defaultVal) {
		if (raw == null || raw.isBlank()) return defaultVal;
		return Boolean.parseBoolean(raw.trim());
	}

	private int bindBody(PreparedStatement ps, int i, boolean activeBool, boolean twt, boolean tss, boolean tp,
			boolean tvr, boolean mb, boolean pop, boolean aux, boolean aip, int po) throws Exception {
		setData(ps, i++, tenantId);
		setData(ps, i++, companyId);
		setData(ps, i++, platformTemplateId);
		setData(ps, i++, code);
		setData(ps, i++, name);
		setData(ps, i++, description);
		setData(ps, i++, componentType);
		setData(ps, i++, category);
		setData(ps, i++, netEffect);
		ps.setBoolean(i++, twt);
		ps.setBoolean(i++, tss);
		ps.setBoolean(i++, tp);
		ps.setBoolean(i++, tvr);
		setData(ps, i++, calculationMethod);
		setData(ps, i++, percentageBase);
		setData(ps, i++, formulaExpression);
		setDecimal(ps, i++, defaultAmount);
		setData(ps, i++, roundingStrategy);
		ps.setInt(i++, po);
		setData(ps, i++, phase);
		ps.setBoolean(i++, mb);
		setData(ps, i++, balanceType);
		setData(ps, i++, balanceDirection);
		setData(ps, i++, counterComponentId);
		setData(ps, i++, postingStrategy);
		ps.setBoolean(i++, pop);
		ps.setBoolean(i++, aux);
		ps.setBoolean(i++, aip);
		setData(ps, i++, recurrence);
		setData(ps, i++, countryRuleKey);
		setData(ps, i++, platformCountryTaxRuleId);
		setData(ps, i++, debitTenantLedgerId);
		setData(ps, i++, creditTenantLedgerId);
		ps.setBoolean(i++, activeBool);
		return i;
	}

	@Override
	public void handleUpdate() throws Exception {
		boolean activeBool = parseBool(active, true);
		boolean twt = parseBool(taxableWageTax, false);
		boolean tss = parseBool(taxableSocialSecurity, false);
		boolean tp = parseBool(taxablePension, false);
		boolean tvr = parseBool(taxableVacationReserve, false);
		boolean mb = parseBool(maintainsBalance, false);
		boolean pop = parseBool(printOnPayslip, true);
		boolean aux = parseBool(auxiliary, false);
		boolean aip = parseBool(applyInPayroll, true);
		int po = Integer.parseInt(processingOrder.trim());

		try (PreparedStatement check = connection.prepareStatement(
				"SELECT COUNT(*) FROM tenant_wage_component WHERE id = ?")) {
			setData(check, 1, id);
			try (ResultSet rs = check.executeQuery()) {
				rs.next();
				if (rs.getInt(1) > 0) {
					try (PreparedStatement ps = connection.prepareStatement("""
							UPDATE tenant_wage_component SET
							  tenant_id = ?, company_id = ?, platform_template_id = ?, code = ?, name = ?, description = ?,
							  component_type = ?, category = ?, net_effect = ?,
							  taxable_wage_tax = ?, taxable_social_security = ?, taxable_pension = ?, taxable_vacation_reserve = ?,
							  calculation_method = ?, percentage_base = ?, formula_expression = ?, default_amount = ?, rounding_strategy = ?,
							  processing_order = ?, phase = ?, maintains_balance = ?, balance_type = ?, balance_direction = ?, counter_component_id = ?,
							  posting_strategy = ?, print_on_payslip = ?, auxiliary = ?, apply_in_payroll = ?, recurrence = ?, country_rule_key = ?, platform_country_tax_rule_id = ?,
							  debit_tenant_ledger_id = ?, credit_tenant_ledger_id = ?, active = ?, updated_at = ?
							WHERE id = ?
							""")) {
						int i = bindBody(ps, 1, activeBool, twt, tss, tp, tvr, mb, pop, aux, aip, po);
						setData(ps, i++, ts);
						setData(ps, i++, id);
						ps.executeUpdate();
					}
					return;
				}
			}
		}
		try (PreparedStatement ps = connection.prepareStatement("""
				INSERT INTO tenant_wage_component (
				  id, tenant_id, company_id, platform_template_id, code, name, description,
				  component_type, category, net_effect,
				  taxable_wage_tax, taxable_social_security, taxable_pension, taxable_vacation_reserve,
				  calculation_method, percentage_base, formula_expression, default_amount, rounding_strategy,
				  processing_order, phase, maintains_balance, balance_type, balance_direction, counter_component_id,
				  posting_strategy, print_on_payslip, auxiliary, apply_in_payroll, recurrence, country_rule_key, platform_country_tax_rule_id,
				  debit_tenant_ledger_id, credit_tenant_ledger_id, active, created_at, updated_at
				) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
				""")) {
			int i = 1;
			setData(ps, i++, id);
			i = bindBody(ps, i, activeBool, twt, tss, tp, tvr, mb, pop, aux, aip, po);
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
	public String getPlatformTemplateId() { return platformTemplateId; }
	public void setPlatformTemplateId(String platformTemplateId) { this.platformTemplateId = platformTemplateId; }
	public String getCode() { return code; }
	public void setCode(String code) { this.code = code; }
	public String getName() { return name; }
	public void setName(String name) { this.name = name; }
	public String getDescription() { return description; }
	public void setDescription(String description) { this.description = description; }
	public String getComponentType() { return componentType; }
	public void setComponentType(String componentType) { this.componentType = componentType; }
	public String getCategory() { return category; }
	public void setCategory(String category) { this.category = category; }
	public String getNetEffect() { return netEffect; }
	public void setNetEffect(String netEffect) { this.netEffect = netEffect; }
	public String getTaxableWageTax() { return taxableWageTax; }
	public void setTaxableWageTax(String taxableWageTax) { this.taxableWageTax = taxableWageTax; }
	public String getTaxableSocialSecurity() { return taxableSocialSecurity; }
	public void setTaxableSocialSecurity(String taxableSocialSecurity) { this.taxableSocialSecurity = taxableSocialSecurity; }
	public String getTaxablePension() { return taxablePension; }
	public void setTaxablePension(String taxablePension) { this.taxablePension = taxablePension; }
	public String getTaxableVacationReserve() { return taxableVacationReserve; }
	public void setTaxableVacationReserve(String taxableVacationReserve) { this.taxableVacationReserve = taxableVacationReserve; }
	public String getCalculationMethod() { return calculationMethod; }
	public void setCalculationMethod(String calculationMethod) { this.calculationMethod = calculationMethod; }
	public String getPercentageBase() { return percentageBase; }
	public void setPercentageBase(String percentageBase) { this.percentageBase = percentageBase; }
	public String getFormulaExpression() { return formulaExpression; }
	public void setFormulaExpression(String formulaExpression) { this.formulaExpression = formulaExpression; }
	public String getDefaultAmount() { return defaultAmount; }
	public void setDefaultAmount(String defaultAmount) { this.defaultAmount = defaultAmount; }
	public String getRoundingStrategy() { return roundingStrategy; }
	public void setRoundingStrategy(String roundingStrategy) { this.roundingStrategy = roundingStrategy; }
	public String getProcessingOrder() { return processingOrder; }
	public void setProcessingOrder(String processingOrder) { this.processingOrder = processingOrder; }
	public String getPhase() { return phase; }
	public void setPhase(String phase) { this.phase = phase; }
	public String getMaintainsBalance() { return maintainsBalance; }
	public void setMaintainsBalance(String maintainsBalance) { this.maintainsBalance = maintainsBalance; }
	public String getBalanceType() { return balanceType; }
	public void setBalanceType(String balanceType) { this.balanceType = balanceType; }
	public String getBalanceDirection() { return balanceDirection; }
	public void setBalanceDirection(String balanceDirection) { this.balanceDirection = balanceDirection; }
	public String getCounterComponentId() { return counterComponentId; }
	public void setCounterComponentId(String counterComponentId) { this.counterComponentId = counterComponentId; }
	public String getPostingStrategy() { return postingStrategy; }
	public void setPostingStrategy(String postingStrategy) { this.postingStrategy = postingStrategy; }
	public String getPrintOnPayslip() { return printOnPayslip; }
	public void setPrintOnPayslip(String printOnPayslip) { this.printOnPayslip = printOnPayslip; }
	public String getAuxiliary() { return auxiliary; }
	public void setAuxiliary(String auxiliary) { this.auxiliary = auxiliary; }
	public String getApplyInPayroll() { return applyInPayroll; }
	public void setApplyInPayroll(String applyInPayroll) { this.applyInPayroll = applyInPayroll; }
	public String getRecurrence() { return recurrence; }
	public void setRecurrence(String recurrence) { this.recurrence = recurrence; }
	public String getCountryRuleKey() { return countryRuleKey; }
	public void setCountryRuleKey(String countryRuleKey) { this.countryRuleKey = countryRuleKey; }
	public String getPlatformCountryTaxRuleId() { return platformCountryTaxRuleId; }
	public void setPlatformCountryTaxRuleId(String platformCountryTaxRuleId) { this.platformCountryTaxRuleId = platformCountryTaxRuleId; }
	public String getDebitTenantLedgerId() { return debitTenantLedgerId; }
	public void setDebitTenantLedgerId(String debitTenantLedgerId) { this.debitTenantLedgerId = debitTenantLedgerId; }
	public String getCreditTenantLedgerId() { return creditTenantLedgerId; }
	public void setCreditTenantLedgerId(String creditTenantLedgerId) { this.creditTenantLedgerId = creditTenantLedgerId; }
	public String getActive() { return active; }
	public void setActive(String active) { this.active = active; }
}
