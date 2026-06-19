package com.wagepayroll.payroll.engine;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Immutable input for a payroll calculation pass (one pay-period run slice).
 *
 * @param payPeriodRunId optional link to a finalized run (may be null during dry runs).
 * @param payPeriodId    when non-null with non-empty {@code employeeIds}, the engine may evaluate
 *                       tenant component formulas against {@code tenant_wage_component_transaction} rows for that period.
 * @param countryRulesAsOf calendar date for versioned {@code platform_country_tax_rule} rows (e.g. pay-period end);
 *                         {@code null} lets country providers default (Suriname: UTC "today").
 */
public record PayrollContext(UUID tenantId, UUID companyId, String payrollCountryIso2, String currencyIso3,
		UUID payPeriodRunId, UUID payPeriodId, List<UUID> employeeIds, LocalDate countryRulesAsOf) {

	public PayrollContext {
		if (payrollCountryIso2 == null || payrollCountryIso2.length() != 2) {
			throw new IllegalArgumentException("payrollCountryIso2 must be ISO-3166-1 alpha-2");
		}
		payrollCountryIso2 = payrollCountryIso2.toUpperCase(java.util.Locale.ROOT);
		employeeIds = employeeIds == null ? List.of() : List.copyOf(employeeIds);
	}

	/**
	 * Same as the 8-arg record constructor with {@code countryRulesAsOf = null}.
	 */
	public static PayrollContext withoutPinnedCountryRules(UUID tenantId, UUID companyId, String payrollCountryIso2,
			String currencyIso3, UUID payPeriodRunId, UUID payPeriodId, List<UUID> employeeIds) {
		return new PayrollContext(tenantId, companyId, payrollCountryIso2, currencyIso3, payPeriodRunId, payPeriodId,
				employeeIds, null);
	}

	public PayrollContext withCountryRulesAsOf(LocalDate countryRulesAsOf) {
		return new PayrollContext(tenantId, companyId, payrollCountryIso2, currencyIso3, payPeriodRunId, payPeriodId,
				employeeIds, countryRulesAsOf);
	}
}
