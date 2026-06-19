package com.wagepayroll.payroll.formula;

public record FormulaRuleDefinition(
		FormulaCriteriaType criteriaType,
		String itemKey,
		String itemLabel,
		String formulaExpression) {
}
