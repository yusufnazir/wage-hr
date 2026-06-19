package com.wagepayroll.payroll.formula;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wagepayroll.payroll.model.CalculationMethod;

class FormulaDefinitionSupportTest {

	private final FormulaDefinitionSupport support = new FormulaDefinitionSupport(new ObjectMapper(),
			new WageComponentFormulaValidator(new ObjectMapper()));

	@Test
	void parseLegacyPlainString() {
		var config = support.parseStoredExpression("compensation.periodic_rate");
		assertThat(config.isCriteriaRules()).isFalse();
		assertThat(config.legacyFormulaExpression()).isEqualTo("compensation.periodic_rate");
	}

	@Test
	void parseCriteriaRulesJson() {
		String json = """
				{"formulaMode":"CRITERIA_RULES","formulaRules":[{"criteriaType":"WAGE_TYPE","itemKey":"PER_HOUR","formulaExpression":"transaction.quantity * transaction.rate"}],"defaultFormulaExpression":"compensation.periodic_rate"}
				""";
		var config = support.parseStoredExpression(json);
		assertThat(config.isCriteriaRules()).isTrue();
		assertThat(config.formulaRules()).hasSize(1);
		assertThat(config.effectiveDefaultFormula()).isEqualTo("compensation.periodic_rate");
	}

	@Test
	void rejectsDuplicateRules() {
		var rule = new FormulaDefinitionSupport.FormulaRuleJson();
		rule.criteriaType = "WAGE_TYPE";
		rule.itemKey = "PER_HOUR";
		rule.formulaExpression = "transaction.quantity * transaction.rate";
		var config = support.configFrom(FormulaDefinitionConfig.MODE_CRITERIA_RULES, List.of(rule, rule),
				"compensation.periodic_rate", null);
		assertThatThrownBy(() -> support.validate(CalculationMethod.FORMULA, null, config, Set.of()))
				.isInstanceOf(ResponseStatusException.class)
				.satisfies(ex -> org.junit.jupiter.api.Assertions.assertEquals(HttpStatus.BAD_REQUEST,
						((ResponseStatusException) ex).getStatusCode()))
				.extracting(ex -> ((ResponseStatusException) ex).getReason())
				.isEqualTo("FORMULA_RULE_DUPLICATE");
	}

	@Test
	void rejectsMissingDefaultForCriteriaRules() {
		var rule = new FormulaDefinitionSupport.FormulaRuleJson();
		rule.criteriaType = "WAGE_TYPE";
		rule.itemKey = "PER_HOUR";
		rule.formulaExpression = "transaction.quantity * transaction.rate";
		var config = support.configFrom(FormulaDefinitionConfig.MODE_CRITERIA_RULES, List.of(rule), null, null);
		assertThatThrownBy(() -> support.validate(CalculationMethod.FORMULA, null, config, Set.of()))
				.extracting(ex -> ((ResponseStatusException) ex).getReason())
				.isEqualTo("FORMULA_DEFAULT_REQUIRED");
	}

	@Test
	void roundTripStoredExpressionCompactsRedundantRules() {
		var config = support.configFrom(FormulaDefinitionConfig.MODE_CRITERIA_RULES,
				FormulaDefinitionSupport.baseSalaryWageTypeRules().stream().map(r -> {
					var j = new FormulaDefinitionSupport.FormulaRuleJson();
					j.criteriaType = r.criteriaType().name();
					j.itemKey = r.itemKey();
					j.itemLabel = r.itemLabel();
					j.formulaExpression = r.formulaExpression();
					return j;
				}).toList(), "compensation.periodic_rate", null);
		String stored = support.toStoredExpression(config);
		assertThat(stored.length()).isLessThanOrEqualTo(500);
		var parsed = support.parseStoredExpression(stored);
		assertThat(parsed.isCriteriaRules()).isTrue();
		assertThat(parsed.formulaRules()).hasSize(1);
		assertThat(parsed.formulaRules().get(0).itemKey()).isEqualTo("PER_HOUR");
	}
}
