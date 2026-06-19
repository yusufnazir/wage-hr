package com.wagepayroll.payroll.formula;

import java.util.Locale;
import java.util.Objects;

import org.springframework.stereotype.Component;

@Component
public class FormulaRuleResolver {

	public String resolveExpression(FormulaDefinitionConfig config, EmployeeFormulaMatchContext employee) {
		if (config == null) {
			return null;
		}
		if (!config.isCriteriaRules()) {
			String legacy = config.legacyFormulaExpression();
			return legacy != null && !legacy.isBlank() ? legacy.trim() : config.effectiveDefaultFormula();
		}
		for (FormulaRuleDefinition rule : config.formulaRules()) {
			if (rule == null || rule.formulaExpression() == null || rule.formulaExpression().isBlank()) {
				continue;
			}
			if (matches(rule, employee)) {
				return rule.formulaExpression().trim();
			}
		}
		return config.effectiveDefaultFormula();
	}

	private static boolean matches(FormulaRuleDefinition rule, EmployeeFormulaMatchContext employee) {
		if (employee == null || rule.itemKey() == null || rule.itemKey().isBlank()) {
			return false;
		}
		String key = rule.itemKey().trim();
		return switch (rule.criteriaType()) {
			case WAGE_TYPE -> key.equalsIgnoreCase(nullToEmpty(employee.wageType()));
			case DEPARTMENT -> key.equalsIgnoreCase(nullToEmpty(employee.departmentCode()));
			case JOB -> key.equalsIgnoreCase(nullToEmpty(employee.jobCode()));
		};
	}

	private static String nullToEmpty(String value) {
		return value != null ? value.trim().toUpperCase(Locale.ROOT) : "";
	}
}
