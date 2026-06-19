package com.wagepayroll.payroll.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WageComponentSortOrderTest {

	@Test
	void netWageSortsAfterStatutoryAndEmployerBands() {
		int net = WageComponentSortOrder.forTemplateCode("1026");
		int fvoEmployer = WageComponentSortOrder.forTemplateCode("1037");
		int salary = WageComponentSortOrder.forTemplateCode("1001");
		assertThat(net).isGreaterThan(fvoEmployer);
		assertThat(fvoEmployer).isGreaterThan(salary);
		assertThat(WageComponentSortBand.SYSTEM_CALCULATIONS.contains(net)).isTrue();
	}

	@Test
	void bandsAreMultiplesOfOneThousand() {
		for (WageComponentSortBand band : WageComponentSortBand.values()) {
			assertThat(band.base() % 1000).isZero();
		}
	}
}
