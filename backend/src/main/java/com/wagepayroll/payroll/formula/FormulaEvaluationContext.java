package com.wagepayroll.payroll.formula;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Bindings available when evaluating a tenant wage component formula during a payroll run.
 */
public record FormulaEvaluationContext(
		BigDecimal compensationPeriodicRate,
		BigDecimal compensationIsHourly,
		BigDecimal compensationHourlyRate,
		BigDecimal transactionQuantity,
		BigDecimal transactionRate,
		BigDecimal transactionAmount,
		BigDecimal definitionDefaultAmount,
		Map<String, BigDecimal> componentAmountsByCode) {

	public FormulaEvaluationContext {
		compensationPeriodicRate = nz(compensationPeriodicRate);
		compensationIsHourly = nz(compensationIsHourly);
		compensationHourlyRate = nz(compensationHourlyRate);
		transactionQuantity = nz(transactionQuantity);
		transactionRate = nz(transactionRate);
		transactionAmount = nz(transactionAmount);
		definitionDefaultAmount = nz(definitionDefaultAmount);
		componentAmountsByCode = componentAmountsByCode == null ? Map.of() : Map.copyOf(componentAmountsByCode);
	}

	public static FormulaEvaluationContext empty() {
		return new FormulaEvaluationContext(null, null, null, null, null, null, null, Map.of());
	}

	public static FormulaEvaluationContext of(BigDecimal compensationPeriodicRate, BigDecimal transactionQuantity,
			BigDecimal transactionRate, BigDecimal transactionAmount, BigDecimal definitionDefaultAmount) {
		return of(compensationPeriodicRate, BigDecimal.ZERO, null, transactionQuantity, transactionRate,
				transactionAmount, definitionDefaultAmount);
	}

	public static FormulaEvaluationContext of(BigDecimal compensationPeriodicRate, BigDecimal compensationIsHourly,
			BigDecimal transactionQuantity, BigDecimal transactionRate, BigDecimal transactionAmount,
			BigDecimal definitionDefaultAmount) {
		return of(compensationPeriodicRate, compensationIsHourly, null, transactionQuantity, transactionRate,
				transactionAmount, definitionDefaultAmount);
	}

	public static FormulaEvaluationContext of(BigDecimal compensationPeriodicRate, BigDecimal compensationIsHourly,
			BigDecimal compensationHourlyRate, BigDecimal transactionQuantity, BigDecimal transactionRate,
			BigDecimal transactionAmount, BigDecimal definitionDefaultAmount) {
		return new FormulaEvaluationContext(compensationPeriodicRate, compensationIsHourly, compensationHourlyRate,
				transactionQuantity, transactionRate, transactionAmount, definitionDefaultAmount, Map.of());
	}

	private static BigDecimal nz(BigDecimal v) {
		return v != null ? v : BigDecimal.ZERO;
	}

	public BigDecimal resolveReference(String ref) {
		return switch (Objects.requireNonNull(ref)) {
			case "compensation.periodic_rate" -> compensationPeriodicRate;
			case "compensation.hourly_rate" -> compensationHourlyRate;
			case "compensation.is_hourly" -> compensationIsHourly;
			case "transaction.quantity" -> transactionQuantity;
			case "transaction.rate" -> transactionRate;
			case "transaction.amount" -> transactionAmount;
			case "definition.default_amount" -> definitionDefaultAmount;
			default -> BigDecimal.ZERO;
		};
	}

	public BigDecimal resolveComponentAmount(String code) {
		if (code == null || code.isBlank()) {
			throw new IllegalArgumentException("COMPONENT_CODE_REQUIRED");
		}
		BigDecimal amount = componentAmountsByCode.get(code);
		if (amount == null) {
			throw new IllegalArgumentException("COMPONENT_AMOUNT_NOT_AVAILABLE:" + code);
		}
		return amount;
	}

	public FormulaEvaluationContext withComponentAmounts(Map<String, BigDecimal> amountsByCode) {
		return new FormulaEvaluationContext(compensationPeriodicRate, compensationIsHourly, compensationHourlyRate,
				transactionQuantity, transactionRate, transactionAmount, definitionDefaultAmount,
				amountsByCode == null ? Map.of() : Collections.unmodifiableMap(amountsByCode));
	}
}
