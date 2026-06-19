package com.wagepayroll.payroll.formula;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wagepayroll.api.dto.FormulaMockContextDto;
import com.wagepayroll.api.dto.WageComponentFormulaValidateRequest;

@SpringBootTest
@ActiveProfiles("test")
class WageComponentFormulaValidateServiceTest {

	@Autowired
	private WageComponentFormulaValidateService validateService;

	@Test
	void formulaMatchesEngineEvaluation() {
		var mock = new FormulaMockContextDto(null, null, null, new BigDecimal("10"), new BigDecimal("15.50"), null, null,
				Map.of("1001", new BigDecimal("18500")));
		var req = new WageComponentFormulaValidateRequest("FORMULA",
				"transaction.quantity * transaction.rate + component(\"1001\").amount * 0.1", null, "HALF_UP", mock);
		var result = validateService.validate(req);
		assertThat(result.ok()).isTrue();
		assertThat(result.amount()).isEqualByComparingTo("2005.0000");
	}

	@Test
	void fixedAmountUsesDefinitionDefault() {
		var mock = new FormulaMockContextDto(null, null, null, null, null, null, new BigDecimal("18500"), null);
		var req = new WageComponentFormulaValidateRequest("FIXED_AMOUNT", null, null, null, mock);
		assertThat(validateService.validate(req).amount()).isEqualByComparingTo("18500.0000");
	}

	@Test
	void invalidSyntaxReturnsInvalidFormula() {
		var mock = new FormulaMockContextDto(null, null, null, null, null, null, null, null);
		var req = new WageComponentFormulaValidateRequest("FORMULA", "compensation.periodic_rate +", null, null, mock);
		assertThatThrownBy(() -> validateService.validate(req)).isInstanceOf(ResponseStatusException.class)
				.satisfies(ex -> assertThat(((ResponseStatusException) ex).getReason()).isEqualTo("INVALID_FORMULA"));
	}

	@Test
	void missingComponentDependencyInMockContext() {
		var mock = new FormulaMockContextDto(null, null, null, null, null, null, null, Map.of());
		var req = new WageComponentFormulaValidateRequest("FORMULA", "component(\"1001\").amount", null, null, mock);
		assertThatThrownBy(() -> validateService.validate(req)).isInstanceOf(ResponseStatusException.class)
				.satisfies(ex -> assertThat(((ResponseStatusException) ex).getReason()).isEqualTo("FORMULA_MISSING_DEPENDENCY"));
	}

	@Test
	void validateServiceUsesSameEvaluatorAsEngine() {
		var evaluator = new WageComponentFormulaEvaluator(new ObjectMapper());
		var mock = new FormulaMockContextDto(new BigDecimal("2500"), null, null, null, null, null, null, null);
		var ctx = new FormulaEvaluationContext(mock.compensationPeriodicRate(), mock.compensationIsHourly(),
				mock.compensationHourlyRate(), mock.transactionQuantity(), mock.transactionRate(),
				mock.transactionAmount(), mock.definitionDefaultAmount(),
				mock.componentAmounts() != null ? mock.componentAmounts() : java.util.Map.of());
		var req = new WageComponentFormulaValidateRequest("FORMULA", "compensation.periodic_rate", null, "HALF_UP",
				mock);
		BigDecimal fromValidate = validateService.validate(req).amount();
		BigDecimal fromEngine = evaluator.evaluate("compensation.periodic_rate", ctx,
				java.math.RoundingMode.HALF_UP);
		assertThat(fromValidate).isEqualByComparingTo(fromEngine);
	}
}
