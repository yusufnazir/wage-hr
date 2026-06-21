package com.wagepayroll.payroll.country;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class SurinameWageTaxCalculatorTest {

	private static final String SR_WAGE_TAX_JSON = """
			{
			  "v": 2,
			  "legacyTariffTypeId": 1,
			  "freq": "YEAR",
			  "kind": "MARGINAL_RATES",
			  "source": "legacy-740-2024",
			  "rows": [
			    { "i": 2, "pct": 8,  "min": 0,      "max": 42000  },
			    { "i": 3, "pct": 18, "min": 42000,  "max": 84000  },
			    { "i": 4, "pct": 28, "min": 84000,  "max": 126000 },
			    { "i": 5, "pct": 38, "min": 126000 }
			  ]
			}
			""";

	private static final String SR_PAYMENTS_AT_ONCE_JSON = """
			{
			  "v": 2,
			  "legacyTariffTypeId": 2,
			  "freq": "YEAR",
			  "kind": "MARGINAL_RATES",
			  "source": "legacy-740-2024",
			  "rows": [
			    { "i": 1, "pct": 5,  "min": 0,      "max": 42000  },
			    { "i": 2, "pct": 15, "min": 42000,  "max": 84000  },
			    { "i": 3, "pct": 25, "min": 84000,  "max": 126000 },
			    { "i": 4, "pct": 35, "min": 126000 }
			  ]
			}
			""";

	private static final String SR_SERVICE_YEARS_JSON = """
			{
			  "v": 2,
			  "legacyTariffTypeId": 4,
			  "freq": "MONTH",
			  "kind": "LEGACY_SERVICE_YEAR_TABLE",
			  "rows": [
			    { "i": 1, "pct": 0,   "lo": 0,  "hi": 9 },
			    { "i": 5, "pct": 100, "lo": 25, "hi": 29 },
			    { "i": 8, "pct": 300, "lo": 40 }
			  ]
			}
			""";

	private final SurinameWageTaxCalculator calculator = new SurinameWageTaxCalculator();

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void computePeriodTaxNormalWageExampleAfterBelastingvrijAndBeroepskosten() throws Exception {
		ResolvedSurinameTaxRule rule = ruleFromJson(SR_WAGE_TAX_JSON);
		assertThat(calculator.computePeriodTax(rule, new BigDecimal("5600.0000"), 12)).isEqualByComparingTo("658.0000");
	}

	@Test
	void computePeriodTaxGoldenScenarioMonthlyBase() throws Exception {
		ResolvedSurinameTaxRule rule = ruleFromJson(SR_WAGE_TAX_JSON);
		BigDecimal periodTax = calculator.computePeriodTax(rule, new BigDecimal("18500.0000"), 12);
		assertThat(periodTax).isEqualByComparingTo("4930.0000");
	}

	@Test
	void computeMarginalTaxOnBaseAnnualGoldenTotal() throws Exception {
		var params = objectMapper.readTree(SR_WAGE_TAX_JSON);
		BigDecimal annualTax = calculator.computeMarginalTaxOnBase(new BigDecimal("222000"), params);
		assertThat(annualTax).isEqualByComparingTo("59160.0000");
	}

	@Test
	void computePeriodTaxZeroWhenBaseZero() throws Exception {
		ResolvedSurinameTaxRule rule = ruleFromJson(SR_WAGE_TAX_JSON);
		assertThat(calculator.computePeriodTax(rule, BigDecimal.ZERO, 12)).isEqualByComparingTo("0.0000");
	}

	@Test
	void computeArt17BijzondereBeloningTaxUsesLabelMethod() throws Exception {
		ResolvedSurinameTaxRule rule = ruleFromJson(SR_WAGE_TAX_JSON);
		BigDecimal labelWage = new BigDecimal("6000.0000");
		BigDecimal vacationPayout = new BigDecimal("12000.0000");
		int nPeriods = 12;
		BigDecimal tax = calculator.computeArt17BijzondereBeloningTax(vacationPayout, nPeriods, labelWage, rule, 12);
		BigDecimal slice = vacationPayout.divide(BigDecimal.valueOf(nPeriods), 4, java.math.RoundingMode.HALF_UP);
		BigDecimal expected = calculator.computePeriodTax(rule, labelWage.add(slice), 12)
				.subtract(calculator.computePeriodTax(rule, labelWage, 12))
				.multiply(BigDecimal.valueOf(nPeriods));
		assertThat(tax).isEqualByComparingTo(expected.setScale(4, java.math.RoundingMode.HALF_UP));
		assertThat(tax.signum()).isPositive();
	}

	@Test
	void computeArt17BijzondereBeloningTaxZeroWhenSpecialRemunerationZero() throws Exception {
		ResolvedSurinameTaxRule rule = ruleFromJson(SR_WAGE_TAX_JSON);
		assertThat(calculator.computeArt17BijzondereBeloningTax(BigDecimal.ZERO, 12, new BigDecimal("6000"), rule, 12))
				.isEqualByComparingTo("0.0000");
	}

	@Test
	void computeJubileeWageTaxAt25YearsUsesMonthlyWagePercentage() throws Exception {
		ResolvedSurinameTaxRule rule = ruleFromJson(SR_SERVICE_YEARS_JSON, "SR_SERVICE_YEARS_17A_MONTH");
		BigDecimal tax = calculator.computeJubileeWageTax(rule, new BigDecimal("6000.0000"), new BigDecimal("6000.0000"),
				25);
		assertThat(tax).isEqualByComparingTo("6000.0000");
	}

	@Test
	void computeJubileeWageTaxZeroWhenTaxableRemainderZero() throws Exception {
		ResolvedSurinameTaxRule rule = ruleFromJson(SR_SERVICE_YEARS_JSON, "SR_SERVICE_YEARS_17A_MONTH");
		assertThat(calculator.computeJubileeWageTax(rule, new BigDecimal("6000.0000"), BigDecimal.ZERO, 25))
				.isEqualByComparingTo("0.0000");
	}

	@Test
	void serviceYearTablePctMatchesTenureBand() throws Exception {
		var params = objectMapper.readTree(SR_SERVICE_YEARS_JSON);
		assertThat(calculator.serviceYearTablePct(25, params)).isEqualByComparingTo("100");
		assertThat(calculator.serviceYearTablePct(8, params)).isEqualByComparingTo("0");
	}

	@Test
	void computePaymentAtOnceTaxOnJubileeTaxableRemainder() throws Exception {
		ResolvedSurinameTaxRule rule = ruleFromJson(SR_PAYMENTS_AT_ONCE_JSON, "SR_PAYMENTS_AT_ONCE_YEAR");
		BigDecimal tax = calculator.computePaymentAtOnceTax(rule, new BigDecimal("7500.0000"));
		assertThat(tax).isEqualByComparingTo("375.0000");
	}

	@Test
	void computePaymentAtOnceTaxUsesBenefitLadderWithoutAnnualization() throws Exception {
		ResolvedSurinameTaxRule rule = ruleFromJson(SR_PAYMENTS_AT_ONCE_JSON, "SR_PAYMENTS_AT_ONCE_YEAR");
		BigDecimal tax = calculator.computePaymentAtOnceTax(rule, new BigDecimal("50000.0000"));
		assertThat(tax).isEqualByComparingTo("3300.0000");
	}

	@Test
	void computePaymentAtOnceTaxZeroWhenBenefitZero() throws Exception {
		ResolvedSurinameTaxRule rule = ruleFromJson(SR_PAYMENTS_AT_ONCE_JSON, "SR_PAYMENTS_AT_ONCE_YEAR");
		assertThat(calculator.computePaymentAtOnceTax(rule, BigDecimal.ZERO)).isEqualByComparingTo("0.0000");
	}

	@Test
	void computeFlatRateAovOnMonthlyBase() throws Exception {
		ResolvedSurinameTaxRule rule = ruleFromJson("""
				{"v":2,"freq":"MONTH","kind":"FLAT_RATE","pct":4}
				""");
		BigDecimal premium = calculator.computePeriodTax(rule, new BigDecimal("18500.0000"), 12);
		assertThat(premium).isEqualByComparingTo("740.0000");
	}

	private ResolvedSurinameTaxRule ruleFromJson(String json) throws Exception {
		return ruleFromJson(json, "SR_WAGE_TAX_DEFAULT");
	}

	private ResolvedSurinameTaxRule ruleFromJson(String json, String ruleCode) throws Exception {
		return new ResolvedSurinameTaxRule(UUID.fromString("52000000-0000-0000-0000-000000000001"), ruleCode,
				"Test rule", LocalDate.of(2024, 1, 1), null, objectMapper.readTree(json));
	}
}
