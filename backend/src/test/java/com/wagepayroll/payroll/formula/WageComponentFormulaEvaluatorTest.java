package com.wagepayroll.payroll.formula;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class WageComponentFormulaEvaluatorTest {

	private final WageComponentFormulaEvaluator evaluator = new WageComponentFormulaEvaluator(new ObjectMapper());

	@Test
	void dslMultipliesQuantityAndRate() {
		var ctx = FormulaEvaluationContext.of(null, new BigDecimal("10"), new BigDecimal("15.50"), null, null);
		BigDecimal v = evaluator.evaluate("transaction.quantity * transaction.rate", ctx, RoundingMode.HALF_UP);
		assertThat(v).isEqualByComparingTo("155.0000");
	}

	@Test
	void dslUsesPeriodicRate() {
		var ctx = FormulaEvaluationContext.of(new BigDecimal("2500"), null, null, null, null);
		assertThat(evaluator.evaluate("compensation.periodic_rate", ctx, RoundingMode.HALF_UP)).isEqualByComparingTo("2500.0000");
	}

	@Test
	void jsonExprEvaluates() {
		String json = """
				{"version":1,"kind":"expr","root":{"op":"mul","left":{"ref":"transaction.quantity"},"right":{"ref":"transaction.rate"}}}
				""";
		var ctx = FormulaEvaluationContext.of(null, new BigDecimal("2"), new BigDecimal("3"), null, null);
		assertThat(evaluator.evaluate(json, ctx, RoundingMode.HALF_UP)).isEqualByComparingTo("6.0000");
	}

	@Test
	void dslUsesComponentAmountReference() {
		var ctx = new FormulaEvaluationContext(null, null, null, null, null, null, null,
				java.util.Map.of("1001", new BigDecimal("18500")));
		BigDecimal v = evaluator.evaluate("component(\"1001\").amount * 0.1", ctx, RoundingMode.HALF_UP);
		assertThat(v).isEqualByComparingTo("1850.0000");
	}

	@Test
	void divisionByZeroThrows() {
		var ctx = FormulaEvaluationContext.of(BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, null, null);
		assertThatThrownBy(() -> evaluator.evaluate("1 / transaction.quantity", ctx, RoundingMode.HALF_UP))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void overtimeUsesHourlyRate() {
		var ctx = FormulaEvaluationContext.of(new BigDecimal("6000"), BigDecimal.ZERO, new BigDecimal("34.6154"),
				new BigDecimal("5"), null, null, null);
		BigDecimal v = evaluator.evaluate("transaction.quantity * compensation.hourly_rate * 1.5", ctx,
				RoundingMode.HALF_UP);
		assertThat(v).isEqualByComparingTo("259.6155");
	}

	@Test
	void ifSelectsHourlyBranch() {
		var ctx = FormulaEvaluationContext.of(new BigDecimal("6000"), BigDecimal.ONE, new BigDecimal("100"),
				new BigDecimal("160"), new BigDecimal("100"), null, null);
		BigDecimal v = evaluator.evaluate(CompensationFormulaSupport.BASE_SALARY_FORMULA, ctx, RoundingMode.HALF_UP);
		assertThat(v).isEqualByComparingTo("16000.0000");
	}

	@Test
	void ifSelectsPeriodicBranch() {
		var ctx = FormulaEvaluationContext.of(new BigDecimal("6000"), BigDecimal.ZERO, new BigDecimal("34.6154"),
				new BigDecimal("160"), new BigDecimal("100"), null, null);
		BigDecimal v = evaluator.evaluate(CompensationFormulaSupport.BASE_SALARY_FORMULA, ctx, RoundingMode.HALF_UP);
		assertThat(v).isEqualByComparingTo("6000.0000");
	}
}
