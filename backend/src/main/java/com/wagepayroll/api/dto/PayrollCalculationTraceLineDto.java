package com.wagepayroll.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PayrollCalculationTraceLineDto(
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
