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
