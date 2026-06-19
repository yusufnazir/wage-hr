package com.wagepayroll.payroll.engine;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.wagepayroll.payroll.base.PayrollBaseContribution;
import com.wagepayroll.payroll.trace.PayrollCalculationTraceCollector;

/**
 * Mutable state for one payroll calculation pass; built by phase handlers and converted to {@link PayrollRunResult}.
 */
public final class PayrollRunState {

	private final PayrollContext context;

	private final CountryRuleContext countryRuleContext;

	private final Map<String, Object> variables = new HashMap<>();

	private int resolvedStatutoryComponentCount;

	private int resolvedTenantComponentCount;

	private final List<EvaluatedComponentAmount> evaluatedComponentAmounts = new ArrayList<>();

	private final List<EvaluatedComponentAmount> statutoryEvaluatedAmounts = new ArrayList<>();

	private Map<UUID, Map<String, BigDecimal>> employeeBaseTotals = Map.of();

	private Map<UUID, Map<String, List<PayrollBaseContribution>>> employeeBaseContributions = Map.of();

	private int persistedResultLineCount;

	private int balancesUpdated;

	private int postingsCreated;

	private Map<UUID, BigDecimal> employeeNetPay = Map.of();

	private final PayrollCalculationTraceCollector calculationTrace = new PayrollCalculationTraceCollector();

	public PayrollRunState(PayrollContext context) {
		this.context = context;
		this.countryRuleContext = new CountryRuleContext(context);
	}

	public PayrollContext context() {
		return context;
	}

	public CountryRuleContext countryRuleContext() {
		return countryRuleContext;
	}

	public Map<String, Object> variables() {
		return variables;
	}

	public int resolvedStatutoryComponentCount() {
		return resolvedStatutoryComponentCount;
	}

	public void setResolvedStatutoryComponentCount(int resolvedStatutoryComponentCount) {
		this.resolvedStatutoryComponentCount = resolvedStatutoryComponentCount;
	}

	public int resolvedTenantComponentCount() {
		return resolvedTenantComponentCount;
	}

	public void setResolvedTenantComponentCount(int resolvedTenantComponentCount) {
		this.resolvedTenantComponentCount = resolvedTenantComponentCount;
	}

	public List<EvaluatedComponentAmount> evaluatedComponentAmounts() {
		return evaluatedComponentAmounts;
	}

	public List<EvaluatedComponentAmount> statutoryEvaluatedAmounts() {
		return statutoryEvaluatedAmounts;
	}

	public Map<UUID, Map<String, BigDecimal>> employeeBaseTotals() {
		return employeeBaseTotals;
	}

	public void setEmployeeBaseTotals(Map<UUID, Map<String, BigDecimal>> employeeBaseTotals) {
		this.employeeBaseTotals = employeeBaseTotals != null ? employeeBaseTotals : Map.of();
	}

	public Map<UUID, Map<String, List<PayrollBaseContribution>>> employeeBaseContributions() {
		return employeeBaseContributions;
	}

	public void setEmployeeBaseContributions(
			Map<UUID, Map<String, List<PayrollBaseContribution>>> employeeBaseContributions) {
		this.employeeBaseContributions = employeeBaseContributions != null ? employeeBaseContributions : Map.of();
	}

	public int persistedResultLineCount() {
		return persistedResultLineCount;
	}

	public void setPersistedResultLineCount(int persistedResultLineCount) {
		this.persistedResultLineCount = persistedResultLineCount;
	}

	public int balancesUpdated() {
		return balancesUpdated;
	}

	public void setBalancesUpdated(int balancesUpdated) {
		this.balancesUpdated = balancesUpdated;
	}

	public int postingsCreated() {
		return postingsCreated;
	}

	public void setPostingsCreated(int postingsCreated) {
		this.postingsCreated = postingsCreated;
	}

	public Map<UUID, BigDecimal> employeeNetPay() {
		return employeeNetPay;
	}

	public void setEmployeeNetPay(Map<UUID, BigDecimal> employeeNetPay) {
		this.employeeNetPay = employeeNetPay != null ? employeeNetPay : Map.of();
	}

	public PayrollCalculationTraceCollector calculationTrace() {
		return calculationTrace;
	}

	public PayrollRunResult toResult() {
		List<EvaluatedComponentAmount> items = new ArrayList<>(evaluatedComponentAmounts.size()
				+ statutoryEvaluatedAmounts.size());
		items.addAll(evaluatedComponentAmounts);
		items.addAll(statutoryEvaluatedAmounts);
		return new PayrollRunResult(resolvedStatutoryComponentCount, resolvedTenantComponentCount, List.copyOf(items),
				employeeBaseTotals, employeeNetPay, persistedResultLineCount, balancesUpdated, postingsCreated,
				calculationTrace.linesByEmployee());
	}

	public void seedContextVariables() {
		variables.put("payrollCountryIso2", context.payrollCountryIso2());
		variables.put("currencyIso3", context.currencyIso3());
		variables.put("countryRulesAsOf", context.countryRulesAsOf());
		variables.put("tenantId", context.tenantId());
		variables.put("companyId", context.companyId());
		variables.put("payPeriodId", context.payPeriodId());
		variables.put("employeeIds", List.copyOf(context.employeeIds()));
	}

	public Map<String, String> countryHintsView() {
		return countryRuleContext.hintsView();
	}
}
