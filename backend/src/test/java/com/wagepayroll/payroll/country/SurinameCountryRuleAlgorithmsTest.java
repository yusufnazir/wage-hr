package com.wagepayroll.payroll.country;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class SurinameCountryRuleAlgorithmsTest {

	private static final String TAX_FREE_JSON = """
			{"v":2,"kind":"THRESHOLD_AMOUNT","freq":"YEAR","amount":108000}
			""";

	private final SurinameCountryRuleAlgorithms algorithms = new SurinameCountryRuleAlgorithms();

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void periodTaxFreeAllowanceDividesAnnualThreshold() throws Exception {
		SurinameTaxRulesSnapshot snapshot = snapshotWithRule(SurinameCountryRuleKeys.RULE_TAX_FREE_WAGE_TAX_YEAR,
				TAX_FREE_JSON);
		assertThat(algorithms.periodTaxFreeAllowance(snapshot, true, 12)).isEqualByComparingTo("9000.0000");
		assertThat(algorithms.periodTaxFreeAllowance(snapshot, false, 12)).isEqualByComparingTo("0.0000");
	}

	@Test
	void periodDeductibleCostsCapsAt4800PerYear() {
		assertThat(algorithms.periodDeductibleCosts(new BigDecimal("18500.0000"), null, 12))
				.isEqualByComparingTo("400.0000");
	}

	@Test
	void periodFreeMedicalBenefitCapsAt200PerYear() {
		assertThat(algorithms.periodFreeMedicalBenefit(new BigDecimal("18500.0000"), 12))
				.isEqualByComparingTo("16.6667");
	}

	@Test
	void periodCompanyCarBenefitAcP2_1() throws Exception {
		SurinameTaxRulesSnapshot snapshot = snapshotWithRule(SurinameCountryRuleKeys.RULE_COMPANY_CAR_YEAR, """
				{"v":2,"freq":"YEAR","kind":"FLAT_RATE","pct":2}
				""");
		assertThat(algorithms.periodCompanyCarBenefit(new BigDecimal("180000.0000"), snapshot, 12))
				.isEqualByComparingTo("300.0000");
		assertThat(algorithms.periodCompanyCarBenefit(BigDecimal.ZERO, snapshot, 12)).isEqualByComparingTo("0.0000");
	}

	@Test
	void periodFreeHousingBenefitAcP2_2() throws Exception {
		SurinameTaxRulesSnapshot snapshot = snapshotWithRule(SurinameCountryRuleKeys.RULE_FREE_HOUSING_YEAR, """
				{"v":2,"freq":"YEAR","kind":"FLAT_RATE","pct":7.5}
				""");
		assertThat(algorithms.periodFreeHousingBenefit(new BigDecimal("8000.0000"), snapshot, 12))
				.isEqualByComparingTo("600.0000");
		assertThat(algorithms.periodFreeHousingBenefit(BigDecimal.ZERO, snapshot, 12)).isEqualByComparingTo("0.0000");
	}

	@Test
	void periodBoardLodgingBenefitAcP2_3() throws Exception {
		SurinameTaxRulesSnapshot snapshot = snapshotWithRule(SurinameCountryRuleKeys.RULE_BOARD_LODGING_DAY, """
				{"v":2,"freq":"MONTH","kind":"UNIT_CAP","amount":10}
				""");
		assertThat(algorithms.periodBoardLodgingBenefit(new BigDecimal("15"), snapshot))
				.isEqualByComparingTo("150.0000");
		assertThat(algorithms.periodBoardLodgingBenefit(BigDecimal.ZERO, snapshot)).isEqualByComparingTo("0.0000");
	}

	@Test
	void periodBoardBenefitAcP2_3b() throws Exception {
		SurinameTaxRulesSnapshot snapshot = snapshotWithRule(SurinameCountryRuleKeys.RULE_BOARD_DAY, """
				{"v":2,"freq":"MONTH","kind":"UNIT_CAP","amount":5}
				""");
		assertThat(algorithms.periodBoardBenefit(new BigDecimal("20"), snapshot)).isEqualByComparingTo("100.0000");
	}

	@Test
	void periodHotMealBenefitAcP2_4() throws Exception {
		SurinameTaxRulesSnapshot snapshot = snapshotWithRule(SurinameCountryRuleKeys.RULE_HOT_MEAL_UNIT, """
				{"v":2,"freq":"MONTH","kind":"UNIT_CAP","amount":5}
				""");
		assertThat(algorithms.periodHotMealBenefit(new BigDecimal("22"), snapshot)).isEqualByComparingTo("110.0000");
	}

	@Test
	void periodBreadMealBenefitAcP2_5() throws Exception {
		SurinameTaxRulesSnapshot snapshot = snapshotWithRule(SurinameCountryRuleKeys.RULE_BREAD_MEAL_UNIT, """
				{"v":2,"freq":"MONTH","kind":"UNIT_CAP","amount":1.5}
				""");
		assertThat(algorithms.periodBreadMealBenefit(new BigDecimal("20"), snapshot)).isEqualByComparingTo("30.0000");
	}

	@Test
	void periodFreeUtilitiesBenefitAcP2_7() {
		assertThat(algorithms.periodFreeUtilitiesBenefit(new BigDecimal("275.50")))
				.isEqualByComparingTo("275.5000");
		assertThat(algorithms.periodFreeUtilitiesBenefit(BigDecimal.ZERO)).isEqualByComparingTo("0.0000");
		assertThat(algorithms.periodFreeUtilitiesBenefit(null)).isEqualByComparingTo("0.0000");
	}

	@Test
	void periodTaxExemptAppliedCannotExceedTaxableIncome() throws Exception {
		SurinameTaxRulesSnapshot snapshot = snapshotWithRule(SurinameCountryRuleKeys.RULE_TAX_FREE_WAGE_TAX_YEAR,
				TAX_FREE_JSON);
		assertThat(algorithms.periodTaxExemptApplied(snapshot, true, 12, new BigDecimal("6900.0000")))
				.isEqualByComparingTo("6900.0000");
		assertThat(algorithms.periodTaxExemptApplied(snapshot, true, 12, new BigDecimal("18500.0000")))
				.isEqualByComparingTo("9000.0000");
	}

	@Test
	void periodChildAllowanceGrossVsArt10hExclusion() throws Exception {
		SurinameTaxRulesSnapshot snapshot2021H1 = snapshotWithRule(
				SurinameCountryRuleKeys.RULE_CHILD_ALLOWANCE_MONTH, """
				{"v":2,"kind":"PER_CHILD_MONTHLY","freq":"MONTH","perChild":75,"maxAmount":300}
				""", LocalDate.of(2021, 3, 31));
		assertThat(algorithms.periodChildAllowanceGrossAmount(snapshot2021H1, new BigDecimal("2")))
				.isEqualByComparingTo("150.0000");
		assertThat(algorithms.periodChildAllowanceGrossAmount(snapshot2021H1, new BigDecimal("5")))
				.isEqualByComparingTo("375.0000");
		assertThat(algorithms.periodChildAllowanceExcludedFromLoon(snapshot2021H1, new BigDecimal("5")))
				.isEqualByComparingTo("300.0000");

		SurinameTaxRulesSnapshot snapshot2026 = snapshotWithRule(
				SurinameCountryRuleKeys.RULE_CHILD_ALLOWANCE_MONTH, """
				{"v":2,"kind":"PER_CHILD_MONTHLY","freq":"MONTH","perChild":125,"maxAmount":500}
				""", LocalDate.of(2026, 2, 28));
		assertThat(algorithms.periodChildAllowanceGrossAmount(snapshot2026, new BigDecimal("3")))
				.isEqualByComparingTo("375.0000");
		assertThat(algorithms.periodChildAllowanceExcludedFromLoon(snapshot2026, new BigDecimal("3")))
				.isEqualByComparingTo("375.0000");
		assertThat(algorithms.periodChildAllowanceGrossAmount(snapshot2026, new BigDecimal("6")))
				.isEqualByComparingTo("750.0000");
		assertThat(algorithms.periodChildAllowanceExcludedFromLoon(snapshot2026, new BigDecimal("6")))
				.isEqualByComparingTo("500.0000");
	}

	@Test
	void adjustTaxableBaseSubtractsChildAllowanceExclusionFromLoon() throws Exception {
		SurinameTaxRulesSnapshot snapshot = snapshotWithRule(SurinameCountryRuleKeys.RULE_CHILD_ALLOWANCE_MONTH, """
				{"v":2,"kind":"PER_CHILD_MONTHLY","freq":"MONTH","perChild":125,"maxAmount":500}
				""");
		assertThat(algorithms.adjustTaxableBaseForWageTax(new BigDecimal("10000.0000"), snapshot, false, 12,
				new BigDecimal("6"))).isEqualByComparingTo("9500.0000");
	}

	@Test
	void periodExchangeRateCompensationExcludedFromLoonAcP2_6() throws Exception {
		SurinameTaxRulesSnapshot snapshot = snapshotWithRule(
				SurinameCountryRuleKeys.RULE_EXCHANGE_RATE_COMPENSATION_MONTH, """
				{"v":2,"kind":"THRESHOLD_AMOUNT","freq":"MONTH","amount":800}
				""");
		assertThat(algorithms.periodExchangeRateCompensationPayout(new BigDecimal("950.0000")))
				.isEqualByComparingTo("950.0000");
		assertThat(algorithms.periodExchangeRateCompensationExcludedFromLoon(snapshot, new BigDecimal("950.0000")))
				.isEqualByComparingTo("800.0000");
		assertThat(algorithms.periodExchangeRateCompensationExcludedFromLoon(snapshot, new BigDecimal("600.0000")))
				.isEqualByComparingTo("600.0000");
	}

	@Test
	void periodCommuteTransportExcludedFromLoonAcP4_2() {
		assertThat(algorithms.periodCommuteTransportPayout(new BigDecimal("1200.0000")))
				.isEqualByComparingTo("1200.0000");
		assertThat(algorithms.periodCommuteTransportExcludedFromLoon(new BigDecimal("1200.0000")))
				.isEqualByComparingTo("1200.0000");
	}

	@Test
	void adjustTaxableBaseSubtractsCommuteTransportExclusionFromLoon() throws Exception {
		SurinameTaxRulesSnapshot snapshot = snapshotWithRule(SurinameCountryRuleKeys.RULE_TAX_FREE_WAGE_TAX_YEAR,
				TAX_FREE_JSON);
		assertThat(algorithms.adjustTaxableBaseForWageTax(new BigDecimal("10000.0000"), snapshot, false, 12, null, null,
				null, null, null, new BigDecimal("1200.0000"))).isEqualByComparingTo("8800.0000");
	}

	@Test
	void periodCostAllowanceExcludedFromLoonAcP4_1() {
		assertThat(algorithms.periodCostAllowancePayout(new BigDecimal("425.0000")))
				.isEqualByComparingTo("425.0000");
		assertThat(algorithms.periodCostAllowanceExcludedFromLoon(new BigDecimal("425.0000")))
				.isEqualByComparingTo("425.0000");
	}

	@Test
	void adjustTaxableBaseSubtractsCostAllowanceExclusionFromLoon() throws Exception {
		SurinameTaxRulesSnapshot snapshot = snapshotWithRule(SurinameCountryRuleKeys.RULE_TAX_FREE_WAGE_TAX_YEAR,
				TAX_FREE_JSON);
		assertThat(algorithms.adjustTaxableBaseForWageTax(new BigDecimal("10000.0000"), snapshot, false, 12, null, null,
				null, null, new BigDecimal("425.0000"))).isEqualByComparingTo("9575.0000");
	}

	@Test
	void periodPension2xAovExcludedFromLoonAcP4_4() throws Exception {
		SurinameTaxRulesSnapshot snapshot = snapshotWithRule(
				SurinameCountryRuleKeys.RULE_AOV_BENEFICIARY_MONTH, """
				{"v":2,"kind":"THRESHOLD_AMOUNT","freq":"MONTH","amount":2250}
				""", LocalDate.of(2026, 2, 28));
		assertThat(algorithms.periodPensionSchemePayout(new BigDecimal("5000.0000")))
				.isEqualByComparingTo("5000.0000");
		assertThat(algorithms.periodPension2xAovExcludedFromLoon(snapshot, new BigDecimal("5000.0000")))
				.isEqualByComparingTo("4500.0000");
	}

	@Test
	void periodPension2xAovExcludedFromLoonFullExclusionAcP4_4b() throws Exception {
		SurinameTaxRulesSnapshot snapshot = snapshotWithRule(
				SurinameCountryRuleKeys.RULE_AOV_BENEFICIARY_MONTH, """
				{"v":2,"kind":"THRESHOLD_AMOUNT","freq":"MONTH","amount":2250}
				""", LocalDate.of(2026, 2, 28));
		assertThat(algorithms.periodPension2xAovExcludedFromLoon(snapshot, new BigDecimal("3000.0000")))
				.isEqualByComparingTo("3000.0000");
	}

	@Test
	void adjustTaxableBaseSubtractsPension2xAovExclusionFromLoon() throws Exception {
		SurinameTaxRulesSnapshot snapshot = snapshotWithRule(
				SurinameCountryRuleKeys.RULE_AOV_BENEFICIARY_MONTH, """
				{"v":2,"kind":"THRESHOLD_AMOUNT","freq":"MONTH","amount":2250}
				""");
		assertThat(algorithms.adjustTaxableBaseForWageTax(new BigDecimal("10000.0000"), snapshot, false, 12, null, null,
				null, new BigDecimal("5000.0000"))).isEqualByComparingTo("5500.0000");
	}

	@Test
	void adjustTaxableBaseSubtractsExchangeRateExclusionFromLoon() throws Exception {
		SurinameTaxRulesSnapshot snapshot = snapshotWithRule(
				SurinameCountryRuleKeys.RULE_EXCHANGE_RATE_COMPENSATION_MONTH, """
				{"v":2,"kind":"THRESHOLD_AMOUNT","freq":"MONTH","amount":800}
				""");
		assertThat(algorithms.adjustTaxableBaseForWageTax(new BigDecimal("10000.0000"), snapshot, false, 12, null, null,
				new BigDecimal("950.0000"))).isEqualByComparingTo("9200.0000");
	}

	@Test
	void adjustTaxableBaseSubtractsBelastingvrijWhenEnabled() throws Exception {
		SurinameTaxRulesSnapshot snapshot = snapshotWithRule(SurinameCountryRuleKeys.RULE_TAX_FREE_WAGE_TAX_YEAR,
				TAX_FREE_JSON);
		assertThat(algorithms.adjustTaxableBaseForWageTax(new BigDecimal("18500.0000"), snapshot, true, 12))
				.isEqualByComparingTo("9500.0000");
		assertThat(algorithms.adjustTaxableBaseForWageTax(new BigDecimal("18500.0000"), snapshot, true, 12, null,
				new BigDecimal("18500.0000"))).isEqualByComparingTo("9100.0000");
	}

	@Test
	void adjustTaxableBaseSubtractsBelastingvrijAndDeductibleCostsForNormalWageExample() throws Exception {
		SurinameTaxRulesSnapshot snapshot = snapshotWithRule(SurinameCountryRuleKeys.RULE_TAX_FREE_WAGE_TAX_YEAR,
				TAX_FREE_JSON);
		BigDecimal labelWage = new BigDecimal("15000.0000");
		assertThat(algorithms.adjustTaxableBaseForWageTax(labelWage, snapshot, true, 12, null, labelWage))
				.isEqualByComparingTo("5600.0000");
	}

	private SurinameTaxRulesSnapshot snapshotWithRule(String ruleCode, String json) throws Exception {
		return snapshotWithRule(ruleCode, json, LocalDate.of(2026, 2, 28));
	}

	private SurinameTaxRulesSnapshot snapshotWithRule(String ruleCode, String json, LocalDate asOf) throws Exception {
		var params = objectMapper.readTree(json);
		ResolvedSurinameTaxRule rule = new ResolvedSurinameTaxRule(UUID.randomUUID(), ruleCode, "Test",
				LocalDate.of(2024, 1, 1), null, params);
		return new SurinameTaxRulesSnapshot(asOf, Map.of(ruleCode, rule));
	}

}
