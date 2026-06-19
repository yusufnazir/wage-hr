package com.wagepayroll.payroll.country;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class SurinameJubileeSupportTest {

	@Test
	void completedServiceYearsUsesWholeYearsThroughAsOf() {
		assertThat(SurinameJubileeSupport.completedServiceYears(LocalDate.of(2001, 3, 15), LocalDate.of(2026, 2, 28)))
				.isEqualTo(24);
		assertThat(SurinameJubileeSupport.completedServiceYears(LocalDate.of(2001, 2, 1), LocalDate.of(2026, 2, 28)))
				.isEqualTo(25);
	}

	@Test
	void art10ExemptAt25YearsIsOneMonthWageCappedToPayout() {
		BigDecimal exempt = SurinameJubileeSupport.art10AnniversaryExemptAmount(new BigDecimal("6000.0000"),
				new BigDecimal("6000.0000"), 25);
		assertThat(exempt).isEqualByComparingTo("6000.0000");
	}

	@Test
	void jubileeScenario4HasZeroTaxableRemainder() {
		var amounts = SurinameJubileeSupport.computeJubileeAmounts(new BigDecimal("6000.0000"),
				new BigDecimal("6000.0000"), 25);
		assertThat(amounts.exempt()).isEqualByComparingTo("6000.0000");
		assertThat(amounts.taxable()).isEqualByComparingTo("0.0000");
	}

	@Test
	void jubileeTaxableRemainderAboveExemptCap() {
		var amounts = SurinameJubileeSupport.computeJubileeAmounts(new BigDecimal("12000.0000"),
				new BigDecimal("6000.0000"), 25);
		assertThat(amounts.exempt()).isEqualByComparingTo("6000.0000");
		assertThat(amounts.taxable()).isEqualByComparingTo("6000.0000");
	}
}
