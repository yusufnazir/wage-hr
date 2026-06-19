package com.wagepayroll.payroll.formula;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class WageComponentFormulaValidatorTest {

	private final WageComponentFormulaValidator validator = new WageComponentFormulaValidator(
			new com.fasterxml.jackson.databind.ObjectMapper());

	@Test
	void dslPeriodicRateOk() {
		validator.validate(com.wagepayroll.payroll.model.CalculationMethod.FORMULA, null,
				"compensation.periodic_rate");
	}

	@Test
	void dslHoursTimesRateOk() {
		validator.validate(com.wagepayroll.payroll.model.CalculationMethod.FORMULA, null,
				"transaction.quantity * transaction.rate");
	}

	@Test
	void dslHourlyRateOk() {
		validator.validate(com.wagepayroll.payroll.model.CalculationMethod.FORMULA, null,
				"transaction.quantity * compensation.hourly_rate * 1.5");
	}

	@Test
	void baseSalaryIfFormulaOk() {
		validator.validate(com.wagepayroll.payroll.model.CalculationMethod.FORMULA, null,
				CompensationFormulaSupport.BASE_SALARY_FORMULA);
	}

	@Test
	void dslUnknownRefRejected() {
		assertThatThrownBy(() -> validator.validate(com.wagepayroll.payroll.model.CalculationMethod.FORMULA, null,
				"unknown.field")).isInstanceOf(ResponseStatusException.class)
				.satisfies(ex -> org.junit.jupiter.api.Assertions.assertEquals(HttpStatus.BAD_REQUEST,
						((ResponseStatusException) ex).getStatusCode()));
	}

	@Test
	void formulaRequiredForFormulaMethod() {
		assertThatThrownBy(() -> validator.validate(com.wagepayroll.payroll.model.CalculationMethod.FORMULA, null, null))
				.isInstanceOf(ResponseStatusException.class)
				.extracting(ex -> ((ResponseStatusException) ex).getReason())
				.isEqualTo("FORMULA_REQUIRED");
	}

	@Test
	void percentageBaseRequired() {
		assertThatThrownBy(() -> validator.validate(com.wagepayroll.payroll.model.CalculationMethod.PERCENTAGE, null, null))
				.isInstanceOf(ResponseStatusException.class)
				.extracting(ex -> ((ResponseStatusException) ex).getReason())
				.isEqualTo("PERCENTAGE_BASE_REQUIRED");
	}

	@Test
	void jsonDslEnvelopeOk() {
		validator.validate(com.wagepayroll.payroll.model.CalculationMethod.FORMULA, null,
				"{\"version\":1,\"kind\":\"dsl\",\"expression\":\"transaction.quantity * transaction.rate\"}");
	}

	@Test
	void jsonExprTreeOk() {
		String json = """
				{"version":1,"kind":"expr","root":{"op":"mul","left":{"ref":"transaction.quantity"},"right":{"ref":"transaction.rate"}}}
				""";
		validator.validate(com.wagepayroll.payroll.model.CalculationMethod.FORMULA, null, json);
	}
}
