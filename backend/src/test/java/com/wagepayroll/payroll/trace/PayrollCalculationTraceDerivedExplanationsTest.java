package com.wagepayroll.payroll.trace;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.wagepayroll.payroll.base.PayrollBaseContribution;
import com.wagepayroll.payroll.country.SurinameCountryRuleKeys;
import com.wagepayroll.payroll.model.PayrollBaseEffectDirection;

class PayrollCalculationTraceDerivedExplanationsTest {

	@Test
	void taxableIncomeExplanationListsLoonbelastingContributions() {
		BigDecimal loonbelasting = new BigDecimal("59497.7502");
		List<PayrollBaseContribution> contributions = List.of(
				new PayrollBaseContribution("1001", "LOONBELASTING", PayrollBaseEffectDirection.INCREASE,
						new BigDecimal("25000"), new BigDecimal("25000")),
				new PayrollBaseContribution("1006", "LOONBELASTING", PayrollBaseEffectDirection.INCREASE,
						new BigDecimal("575"), new BigDecimal("575")));
		String explanation = PayrollCalculationTraceDerivedExplanations.amountForCountryRule(
				SurinameCountryRuleKeys.TAXABLE_INCOME, loonbelasting, BigDecimal.ZERO, null, null, loonbelasting,
				Map.of("LOONBELASTING", contributions));

		assertThat(explanation).contains("Taxable income line = LOONBELASTING base 59497.7502");
		assertThat(explanation).contains("LOONBELASTING from wage components:");
		assertThat(explanation).contains("+ [1001] 25000");
		assertThat(explanation).contains("+ [1006] 575");
		assertThat(explanation).contains("Total LOONBELASTING: 59497.7502");
	}

	@Test
	void formulaDependencyBreakdownListsReferencedComponents() {
		Map<String, BigDecimal> amounts = Map.of("1001", new BigDecimal("25000"), "1002", new BigDecimal("500"));
		String breakdown = PayrollCalculationTraceSupport.formatFormulaComponentDependencies(List.of("1001", "1002"),
				amounts);

		assertThat(breakdown).contains("[1001] 25000");
		assertThat(breakdown).contains("[1002] 500");
	}
}
