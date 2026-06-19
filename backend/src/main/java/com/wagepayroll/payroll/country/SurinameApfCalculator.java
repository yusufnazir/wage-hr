package com.wagepayroll.payroll.country;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Suriname APF (Algemeen Pensioenfonds) — monthly premium from gross salary with min/max base
 * and a calendar-year total rate schedule ({@code SR_AP_CONTRIBUTION_MONTH}).
 * <p>
 * Employer and employee each pay half of the total premium on the same clamped period base.
 */
@Component
public class SurinameApfCalculator {

	public static final String RULE_CODE = "SR_AP_CONTRIBUTION_MONTH";

	private static final RoundingMode ROUND = RoundingMode.HALF_UP;

	private static final int SCALE = 4;

	private static final BigDecimal DEFAULT_MIN_MONTHLY = new BigDecimal("500");

	private static final BigDecimal DEFAULT_MAX_MONTHLY = new BigDecimal("5000");

	private static final BigDecimal DEFAULT_EMPLOYEE_SHARE_PCT = new BigDecimal("50");

	private static final BigDecimal HUNDRED = new BigDecimal("100");

	/**
	 * One party share (employee or employer) for the pay period.
	 *
	 * @param periodGrossBase accumulated {@code GROSS} base for the period (before APF lines)
	 * @param calendarYear    year of {@code countryRulesAsOf} (pay-period end)
	 */
	public BigDecimal computePartyShare(BigDecimal periodGrossBase, int calendarYear, ResolvedSurinameTaxRule rule) {
		if (periodGrossBase == null || periodGrossBase.signum() <= 0 || rule == null) {
			return BigDecimal.ZERO.setScale(SCALE, ROUND);
		}
		JsonNode params = rule.parameters();
		if (params == null || params.isMissingNode()) {
			return BigDecimal.ZERO.setScale(SCALE, ROUND);
		}
		ApfYearRow row = resolveYearRow(params, calendarYear);
		BigDecimal clampedBase = clamp(periodGrossBase, row.minimumAmount(), row.maximumAmount());
		BigDecimal employeeSharePct = decimal(params, "employeeSharePct", DEFAULT_EMPLOYEE_SHARE_PCT);
		BigDecimal partyFraction = employeeSharePct.divide(HUNDRED, SCALE + 4, ROUND);
		BigDecimal totalRate = row.percentage().divide(HUNDRED, SCALE + 4, ROUND);
		return clampedBase.multiply(totalRate).multiply(partyFraction).setScale(SCALE, ROUND);
	}

	private static BigDecimal clamp(BigDecimal base, BigDecimal min, BigDecimal max) {
		BigDecimal result = base;
		if (min != null && result.compareTo(min) < 0) {
			result = min;
		}
		if (max != null && result.compareTo(max) > 0) {
			result = max;
		}
		return result;
	}

	private static ApfYearRow resolveYearRow(JsonNode params, int calendarYear) {
		BigDecimal min = decimal(params, "defaultMinimumAmount", DEFAULT_MIN_MONTHLY);
		BigDecimal max = decimal(params, "defaultMaximumAmount", DEFAULT_MAX_MONTHLY);
		JsonNode rows = params.get("rows");
		if (rows != null && rows.isArray()) {
			ApfYearRow exact = null;
			ApfYearRow latestBefore = null;
			ApfYearRow earliestAfter = null;
			for (JsonNode node : rows) {
				int year = node.path("year").asInt(-1);
				if (year < 0) {
					continue;
				}
				BigDecimal rowMin = decimal(node, "minimumAmount", min);
				BigDecimal rowMax = decimal(node, "maximumAmount", max);
				BigDecimal pct = decimal(node, "percentage", null);
				if (pct == null) {
					continue;
				}
				ApfYearRow row = new ApfYearRow(year, rowMin, rowMax, pct);
				if (year == calendarYear) {
					exact = row;
					break;
				}
				if (year < calendarYear) {
					if (latestBefore == null || year > latestBefore.year()) {
						latestBefore = row;
					}
				}
				else if (earliestAfter == null || year < earliestAfter.year()) {
					earliestAfter = row;
				}
			}
			if (exact != null) {
				return exact;
			}
			if (latestBefore != null) {
				return latestBefore;
			}
			if (earliestAfter != null) {
				return earliestAfter;
			}
		}
		return new ApfYearRow(calendarYear, min, max, fallbackTotalPremiumPct(calendarYear));
	}

	/**
	 * Pre-2025 schedule: 3% total in 2015, +0.5% per calendar year from 2016; cap 28% from 2065 onward.
	 */
	static BigDecimal fallbackTotalPremiumPct(int calendarYear) {
		if (calendarYear < 2015) {
			return BigDecimal.ZERO.setScale(2, ROUND);
		}
		if (calendarYear >= 2065) {
			return new BigDecimal("28.00");
		}
		if (calendarYear == 2015) {
			return new BigDecimal("3.00");
		}
		double pct = 3.0 + (calendarYear - 2015) * 0.5;
		return BigDecimal.valueOf(pct).setScale(2, ROUND);
	}

	private static BigDecimal decimal(JsonNode node, String field, BigDecimal fallback) {
		JsonNode value = node.get(field);
		if (value == null || value.isNull() || !value.isNumber()) {
			return fallback;
		}
		return value.decimalValue();
	}

	private record ApfYearRow(int year, BigDecimal minimumAmount, BigDecimal maximumAmount, BigDecimal percentage) {
	}

}
