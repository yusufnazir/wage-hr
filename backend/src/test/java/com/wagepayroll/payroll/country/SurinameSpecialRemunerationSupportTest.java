package com.wagepayroll.payroll.country;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wagepayroll.payroll.engine.EvaluatedComponentAmount;

class SurinameSpecialRemunerationSupportTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void exemptPortionUsesMonthWageAndAnnualRuleCap() throws Exception {
		SurinameTaxRulesSnapshot snapshot = snapshotWithVacationRule();
		BigDecimal exempt = SurinameSpecialRemunerationSupport.exemptPortion(new BigDecimal("25000.0000"),
				new BigDecimal("20000.0000"), snapshot, SurinameCountryRuleKeys.RULE_TAX_FREE_VACATION_YEAR, 12);
		assertThat(exempt).isEqualByComparingTo("19500.0000");
	}

	@Test
	void labelWageExcludesVacationAndBonusFromLoonbelasting() throws Exception {
		SurinameTaxRulesSnapshot snapshot = snapshotWithVacationRule();
		var amounts = SurinameSpecialRemunerationSupport.compute(new BigDecimal("35000.0000"), new BigDecimal("25000.0000"),
				new BigDecimal("3000.0000"), new BigDecimal("500.0000"), BigDecimal.ZERO, BigDecimal.ZERO,
				new BigDecimal("800.0000"), new BigDecimal("20000.0000"), null, snapshot, 12);
		assertThat(amounts.labelPeriodWage()).isEqualByComparingTo("5700.0000");
		assertThat(amounts.vacationExempt()).isEqualByComparingTo("19500.0000");
		assertThat(amounts.vacationTaxable()).isEqualByComparingTo("5500.0000");
		assertThat(amounts.bonusTaxable()).isEqualByComparingTo("0.0000");
	}

	@Test
	void overtimePayoutSumsAllOvertimeTemplates() {
		UUID employee = UUID.randomUUID();
		var lines = List.of(
				EvaluatedComponentAmount.tenant(employee, UUID.randomUUID(), "1045", "FORMULA",
						new BigDecimal("100.0000"), null),
				EvaluatedComponentAmount.tenant(employee, UUID.randomUUID(), "1046", "FORMULA",
						new BigDecimal("50.0000"), null));
		assertThat(SurinameSpecialRemunerationSupport.overtimePayoutForEmployee(lines, employee))
				.isEqualByComparingTo("150.0000");
	}

	@Test
	void extraEarningsAreFullyTaxableWithNoExemptPortion() throws Exception {
		SurinameTaxRulesSnapshot snapshot = snapshotWithVacationRule();
		var amounts = SurinameSpecialRemunerationSupport.compute(new BigDecimal("10000.0000"), BigDecimal.ZERO,
				BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("1200.0000"),
				new BigDecimal("6000.0000"), null, snapshot, 12);
		assertThat(amounts.extraEarningsTaxable()).isEqualByComparingTo("1200.0000");
		assertThat(amounts.labelPeriodWage()).isEqualByComparingTo("8800.0000");
	}

	@Test
	void labelWageExcludesLumpSumFromLoonbelasting() throws Exception {
		SurinameTaxRulesSnapshot snapshot = snapshotWithVacationRule();
		var amounts = SurinameSpecialRemunerationSupport.compute(new BigDecimal("60000.0000"), BigDecimal.ZERO,
				BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("50000.0000"), BigDecimal.ZERO, BigDecimal.ZERO,
				new BigDecimal("6000.0000"), null, snapshot, 12);
		assertThat(amounts.lumpSumPayout()).isEqualByComparingTo("50000.0000");
		assertThat(amounts.labelPeriodWage()).isEqualByComparingTo("10000.0000");
	}

	@Test
	void payoutForCodeSumsPassOneLines() {
		UUID employee = UUID.randomUUID();
		UUID comp = UUID.randomUUID();
		var lines = List.of(EvaluatedComponentAmount.tenant(employee, comp, "1006", "FIXED_AMOUNT",
				new BigDecimal("1500.0000"), null));
		assertThat(SurinameSpecialRemunerationSupport.payoutForCode(lines, employee, "1006"))
				.isEqualByComparingTo("1500.0000");
	}

	private SurinameTaxRulesSnapshot snapshotWithVacationRule() throws Exception {
		ResolvedSurinameTaxRule vacationFree = new ResolvedSurinameTaxRule(
				UUID.fromString("52000000-0000-0000-0000-000000000008"), "SR_TAX_FREE_VACATION_YEAR", "Vacation free",
				LocalDate.of(2025, 7, 1), null, objectMapper.readTree("""
						{"v":2,"freq":"YEAR","kind":"THRESHOLD_AMOUNT","amount":19500}
						"""));
		return new SurinameTaxRulesSnapshot(LocalDate.of(2026, 2, 28),
				Map.of("SR_TAX_FREE_VACATION_YEAR", vacationFree));
	}
}
