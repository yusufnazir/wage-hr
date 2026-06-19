package com.wagepayroll.payroll.base;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Payroll base totals plus per-component contributions used to build each base.
 */
public record PayrollBaseAccumulationResult(
		Map<UUID, Map<String, BigDecimal>> totalsByEmployee,
		Map<UUID, Map<String, List<PayrollBaseContribution>>> contributionsByEmployee) {

	public PayrollBaseAccumulationResult {
		totalsByEmployee = totalsByEmployee != null ? totalsByEmployee : Map.of();
		contributionsByEmployee = contributionsByEmployee != null ? contributionsByEmployee : Map.of();
	}

	public List<PayrollBaseContribution> contributionsFor(UUID employeeId, String baseCode) {
		return contributionsByEmployee.getOrDefault(employeeId, Map.of()).getOrDefault(baseCode, List.of());
	}

	public static PayrollBaseAccumulationResult empty() {
		return new PayrollBaseAccumulationResult(Map.of(), Map.of());
	}

	public static PayrollBaseAccumulationResult of(Map<UUID, Map<String, BigDecimal>> totals,
			Map<UUID, Map<String, List<PayrollBaseContribution>>> contributions) {
		return new PayrollBaseAccumulationResult(
				Collections.unmodifiableMap(totals),
				Collections.unmodifiableMap(contributions));
	}
}
