package com.wagepayroll.payroll.formula;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class FormulaRuleResolverTest {

	private final FormulaRuleResolver resolver = new FormulaRuleResolver();

	@Test
	void legacyStringExpressionUsedWhenNotCriteriaRules() {
		var config = new FormulaDefinitionConfig(null, List.of(), null, "compensation.periodic_rate");
		assertThat(resolver.resolveExpression(config, new EmployeeFormulaMatchContext("PER_MONTH", null, null)))
				.isEqualTo("compensation.periodic_rate");
	}

	@Test
	void wageTypeFirstMatchWins() {
		var config = criteriaConfig(FormulaDefinitionSupport.baseSalaryWageTypeRules(), "compensation.periodic_rate");
		assertThat(resolver.resolveExpression(config, new EmployeeFormulaMatchContext("PER_HOUR", null, null)))
				.isEqualTo("transaction.quantity * transaction.rate");
		assertThat(resolver.resolveExpression(config, new EmployeeFormulaMatchContext("PER_PERIOD", null, null)))
				.isEqualTo("compensation.periodic_rate");
	}

	@Test
	void departmentCodeMatch() {
		var rules = List.of(new FormulaRuleDefinition(FormulaCriteriaType.DEPARTMENT, "OPS", "Operations",
				"definition.default_amount"));
		var config = criteriaConfig(rules, "compensation.periodic_rate");
		assertThat(resolver.resolveExpression(config, new EmployeeFormulaMatchContext("PER_MONTH", "OPS", null)))
				.isEqualTo("definition.default_amount");
		assertThat(resolver.resolveExpression(config, new EmployeeFormulaMatchContext("PER_MONTH", "HR", null)))
				.isEqualTo("compensation.periodic_rate");
	}

	@Test
	void jobCodeMatch() {
		var rules = List.of(
				new FormulaRuleDefinition(FormulaCriteriaType.JOB, "DEV-01", "Developer", "transaction.rate * 2"));
		var config = criteriaConfig(rules, "compensation.periodic_rate");
		assertThat(resolver.resolveExpression(config, new EmployeeFormulaMatchContext("PER_MONTH", null, "DEV-01")))
				.isEqualTo("transaction.rate * 2");
	}

	@Test
	void firstMatchingRuleInListOrder() {
		var rules = List.of(
				new FormulaRuleDefinition(FormulaCriteriaType.WAGE_TYPE, "PER_PERIOD", "Per period",
						"compensation.periodic_rate"),
				new FormulaRuleDefinition(FormulaCriteriaType.DEPARTMENT, "OPS", "Operations",
						"definition.default_amount"));
		var config = criteriaConfig(rules, "transaction.amount");
		assertThat(resolver.resolveExpression(config, new EmployeeFormulaMatchContext("PER_PERIOD", "OPS", null)))
				.isEqualTo("compensation.periodic_rate");
	}

	@Test
	void defaultFallbackWhenNoRuleMatches() {
		var config = criteriaConfig(FormulaDefinitionSupport.baseSalaryWageTypeRules(), "compensation.periodic_rate");
		assertThat(resolver.resolveExpression(config, new EmployeeFormulaMatchContext("UNKNOWN", null, null)))
				.isEqualTo("compensation.periodic_rate");
	}

	private static FormulaDefinitionConfig criteriaConfig(List<FormulaRuleDefinition> rules, String defaultExpr) {
		return new FormulaDefinitionConfig(FormulaDefinitionConfig.MODE_CRITERIA_RULES, rules, defaultExpr, null);
	}
}
