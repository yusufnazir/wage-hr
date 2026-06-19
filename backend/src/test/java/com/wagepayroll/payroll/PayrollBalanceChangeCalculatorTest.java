package com.wagepayroll.payroll;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.wagepayroll.payroll.model.NetEffect;

class PayrollBalanceChangeCalculatorTest {

	@Test
	void loanRepaymentDecreasesDebitNormalBalance() {
		BigDecimal change = PayrollBalanceChangeCalculator.computeChangeAmount(new BigDecimal("500.0000"), "DEBIT",
				NetEffect.SUBTRACT_FROM_NET);
		assertThat(change).isEqualByComparingTo("-500.0000");
	}

	@Test
	void zeroAmountReturnsZero() {
		assertThat(PayrollBalanceChangeCalculator.computeChangeAmount(BigDecimal.ZERO, "DEBIT",
				NetEffect.SUBTRACT_FROM_NET)).isEqualByComparingTo("0");
	}
}
