package com.wagepayroll.payroll.trace;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One row in a user-facing payroll calculation trace (processing order).
 */
public record PayrollCalculationTraceLine(
		int sequence,
		String enginePhase,
		UUID employeeId,
		String componentCode,
		String componentName,
		String componentSource,
		String componentType,
		String category,
		String netEffect,
		String payEffect,
		String taxationSummary,
		String calculationMethod,
		String countryRuleKey,
		Integer processingOrder,
		BigDecimal factorQuantity,
		BigDecimal factorRate,
		String factorExplanation,
		BigDecimal amount,
		String amountExplanation,
		String formulaExpression,
		boolean includedInResult,
		String skipReason) {
}
