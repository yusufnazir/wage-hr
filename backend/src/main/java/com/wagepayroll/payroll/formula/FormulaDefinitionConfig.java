package com.wagepayroll.payroll.formula;

import java.util.List;

/**
 * Parsed formula configuration: legacy single expression or criteria-based rules.
 */
public record FormulaDefinitionConfig(
		String formulaMode,
		List<FormulaRuleDefinition> formulaRules,
		String defaultFormulaExpression,
		String legacyFormulaExpression) {

	public static final String MODE_CRITERIA_RULES = "CRITERIA_RULES";

	public boolean isCriteriaRules() {
		return MODE_CRITERIA_RULES.equalsIgnoreCase(formulaMode) && formulaRules != null && !formulaRules.isEmpty();
	}

	public String effectiveDefaultFormula() {
		if (defaultFormulaExpression != null && !defaultFormulaExpression.isBlank()) {
			return defaultFormulaExpression.trim();
		}
		return legacyFormulaExpression != null ? legacyFormulaExpression.trim() : null;
	}
}
