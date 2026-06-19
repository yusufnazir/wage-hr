package com.wagepayroll.payroll.engine;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Outcome of a payroll engine execution. Lines and balances are populated in later milestones.
 * {@link #evaluatedComponentAmounts()} holds per-employee formula/hourly/fixed/manual previews when
 * {@link PayrollContext#payPeriodId()} and employees are present.
 * {@link #employeeBaseTotals()} maps employee id → payroll base code → accumulated amount.
 */
public record PayrollRunResult(int resolvedStatutoryComponentCount, int resolvedTenantComponentCount,
		List<EvaluatedComponentAmount> evaluatedComponentAmounts,
		Map<UUID, Map<String, BigDecimal>> employeeBaseTotals, Map<UUID, BigDecimal> employeeNetPay,
		int persistedResultLineCount, int balancesUpdated, int postingsCreated,
		Map<UUID, List<com.wagepayroll.payroll.trace.PayrollCalculationTraceLine>> employeeCalculationTrace) {

	public static final PayrollRunResult EMPTY = new PayrollRunResult(0, 0, List.of(), Map.of(), Map.of(), 0, 0, 0,
			Map.of());
}
