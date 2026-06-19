package com.wagepayroll.payroll.country;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

/**
 * Suriname FVO premium — 1% of monthly base salary without emoluments (toelagen, vakantie, bonus,
 * overwerk), split equally: 0.5% employee and 0.5% employer ({@code SR_FVO_PREMIUM_MONTH}).
 */
@Component
public class SurinameFvoCalculator {

	public static final String RULE_CODE = "SR_FVO_PREMIUM_MONTH";

	private final SurinameWageTaxCalculator wageTaxCalculator;

	public SurinameFvoCalculator(SurinameWageTaxCalculator wageTaxCalculator) {
		this.wageTaxCalculator = wageTaxCalculator;
	}

	/**
	 * @param basisLoonWithoutEmoluments label/base wage for the period (regular pay only)
	 */
	public BigDecimal computePartyShare(BigDecimal basisLoonWithoutEmoluments, ResolvedSurinameTaxRule rule) {
		if (basisLoonWithoutEmoluments == null || basisLoonWithoutEmoluments.signum() <= 0 || rule == null) {
			return BigDecimal.ZERO.setScale(4, java.math.RoundingMode.HALF_UP);
		}
		return wageTaxCalculator.computePeriodTax(rule, basisLoonWithoutEmoluments,
				SurinameWageTaxCalculator.DEFAULT_PERIODS_PER_YEAR);
	}

}
