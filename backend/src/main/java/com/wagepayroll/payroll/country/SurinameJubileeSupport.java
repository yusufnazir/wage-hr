package com.wagepayroll.payroll.country;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Art. 10 anniversary exemption and jubilee payout inputs (template 1010).
 */
public final class SurinameJubileeSupport {

	public static final String JUBILEE_COMPONENT_CODE = "1010";

	private static final RoundingMode ROUND = RoundingMode.HALF_UP;

	private static final int SCALE = 4;

	/** Art. 10 anniversary exemption as fraction of one month's wage (§4.4). */
	private static final NavigableMap<Integer, BigDecimal> ART10_EXEMPT_FRACTION_BY_MILESTONE = new TreeMap<>(Map.of(10,
			new BigDecimal("0.25"), 15, new BigDecimal("0.50"), 20, new BigDecimal("0.75"), 25, BigDecimal.ONE, 30,
			new BigDecimal("1.50"), 35, new BigDecimal("2.00"), 40, new BigDecimal("3.00")));

	private SurinameJubileeSupport() {
	}

	public record JubileeAmounts(BigDecimal payout, BigDecimal exempt, BigDecimal taxable, Integer serviceYears) {
	}

	public static JubileeAmounts computeJubileeAmounts(BigDecimal jubileePayout, BigDecimal referenceMonthWage,
			Integer serviceYears) {
		BigDecimal payout = nz(jubileePayout);
		if (payout.signum() <= 0) {
			return new JubileeAmounts(zero(), zero(), zero(), serviceYears);
		}
		BigDecimal exempt = art10AnniversaryExemptAmount(payout, referenceMonthWage, serviceYears);
		BigDecimal taxable = payout.subtract(exempt).max(zero());
		return new JubileeAmounts(payout, exempt, taxable, serviceYears);
	}

	/**
	 * Completed whole years of service from {@code hireDate} through {@code asOf} (inclusive).
	 */
	public static Integer completedServiceYears(LocalDate hireDate, LocalDate asOf) {
		if (hireDate == null || asOf == null || hireDate.isAfter(asOf)) {
			return null;
		}
		return Period.between(hireDate, asOf).getYears();
	}

	static BigDecimal art10AnniversaryExemptAmount(BigDecimal payout, BigDecimal referenceMonthWage,
			Integer serviceYears) {
		if (payout.signum() <= 0 || serviceYears == null || serviceYears < 10) {
			return zero();
		}
		BigDecimal monthRef = referenceMonthWage != null && referenceMonthWage.signum() > 0
				? referenceMonthWage.setScale(SCALE, ROUND)
				: zero();
		if (monthRef.signum() <= 0) {
			return zero();
		}
		Map.Entry<Integer, BigDecimal> milestone = ART10_EXEMPT_FRACTION_BY_MILESTONE.floorEntry(serviceYears);
		if (milestone == null) {
			return zero();
		}
		BigDecimal cap = monthRef.multiply(milestone.getValue()).setScale(SCALE, ROUND);
		return payout.min(cap).setScale(SCALE, ROUND);
	}

	private static BigDecimal nz(BigDecimal value) {
		return value != null ? value.setScale(SCALE, ROUND) : zero();
	}

	private static BigDecimal zero() {
		return BigDecimal.ZERO.setScale(SCALE, ROUND);
	}
}
