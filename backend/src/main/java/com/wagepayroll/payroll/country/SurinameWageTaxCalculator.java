package com.wagepayroll.payroll.country;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Suriname wage tax and flat-rate statutory calculators from versioned {@code parameters_json}.
 */
@Component
public class SurinameWageTaxCalculator {

	public static final int DEFAULT_PERIODS_PER_YEAR = 12;

	private static final RoundingMode ROUND = RoundingMode.HALF_UP;

	private static final int SCALE = 4;

	/**
	 * Art. 17 lid 2 Wet Loonbelasting — bijzondere beloning (label method).
	 * <p>
	 * Used for wage tax on vacation allowance and bonus (templates 1021 / 1022), not for
	 * folding the full payment into {@code LOONBELASTING} and running a single normal ladder pass.
	 *
	 * @param specialRemuneration     total special payment in the payout period (belast + exempt handled upstream)
	 * @param periodCount           N — number of normal pay periods the payment relates to (art. 17 lid 2e)
	 * @param normalPeriodWage      label-taxed wage for the same period (regular pay only)
	 * @param wageTaxRule           typically {@code SR_WAGE_TAX_DEFAULT}
	 * @param periodCountPerYear    periods per year (default 12)
	 * @return period withholding attributable to the special remuneration
	 */
	public BigDecimal computeArt17BijzondereBeloningTax(BigDecimal specialRemuneration, int periodCount,
			BigDecimal normalPeriodWage, ResolvedSurinameTaxRule wageTaxRule, int periodCountPerYear) {
		if (specialRemuneration == null || specialRemuneration.signum() <= 0 || periodCount <= 0
				|| wageTaxRule == null) {
			return BigDecimal.ZERO.setScale(SCALE, ROUND);
		}
		BigDecimal label = normalPeriodWage != null ? normalPeriodWage : BigDecimal.ZERO;
		BigDecimal slice = specialRemuneration.divide(BigDecimal.valueOf(periodCount), SCALE + 4, ROUND);
		BigDecimal combined = label.add(slice);
		BigDecimal taxOnCombined = computePeriodTax(wageTaxRule, combined, periodCountPerYear);
		BigDecimal taxOnLabelOnly = computePeriodTax(wageTaxRule, label, periodCountPerYear);
		BigDecimal difference = taxOnCombined.subtract(taxOnLabelOnly);
		if (difference.signum() < 0) {
			difference = BigDecimal.ZERO;
		}
		return difference.multiply(BigDecimal.valueOf(periodCount)).setScale(SCALE, ROUND);
	}

	/**
	 * Art. 17a payments-at-once table — progressive marginal tax on the benefit amount per payment
	 * (bracket amounts apply directly; no Policy A annualization).
	 */
	public BigDecimal computePaymentAtOnceTax(ResolvedSurinameTaxRule rule, BigDecimal benefitAmount) {
		if (rule == null || benefitAmount == null || benefitAmount.signum() <= 0) {
			return BigDecimal.ZERO.setScale(SCALE, ROUND);
		}
		JsonNode params = rule.parameters();
		if (params == null || params.isMissingNode() || !"MARGINAL_RATES".equals(text(params, "kind"))) {
			return BigDecimal.ZERO.setScale(SCALE, ROUND);
		}
		return computeMarginalTaxOnBase(benefitAmount, params);
	}

	/**
	 * Art. 17a jubilee wage tax — {@code LEGACY_SERVICE_YEAR_TABLE} percentage of reference month wage
	 * when a taxable remainder exists after Art. 10 anniversary exemption.
	 */
	public BigDecimal computeJubileeWageTax(ResolvedSurinameTaxRule rule, BigDecimal referenceMonthWage,
			BigDecimal taxableRemainder, int serviceYears) {
		if (rule == null || taxableRemainder == null || taxableRemainder.signum() <= 0 || referenceMonthWage == null
				|| referenceMonthWage.signum() <= 0 || serviceYears < 0) {
			return BigDecimal.ZERO.setScale(SCALE, ROUND);
		}
		JsonNode params = rule.parameters();
		if (params == null || params.isMissingNode() || !"LEGACY_SERVICE_YEAR_TABLE".equals(text(params, "kind"))) {
			return BigDecimal.ZERO.setScale(SCALE, ROUND);
		}
		BigDecimal pct = serviceYearTablePct(serviceYears, params);
		if (pct == null || pct.signum() <= 0) {
			return BigDecimal.ZERO.setScale(SCALE, ROUND);
		}
		BigDecimal rate = pct.divide(BigDecimal.valueOf(100), SCALE + 4, ROUND);
		return referenceMonthWage.multiply(rate).setScale(SCALE, ROUND);
	}

	BigDecimal serviceYearTablePct(int serviceYears, JsonNode params) {
		JsonNode rowsNode = params.get("rows");
		if (rowsNode == null || !rowsNode.isArray() || rowsNode.isEmpty()) {
			return null;
		}
		BigDecimal matched = null;
		for (JsonNode row : rowsNode) {
			Integer lo = intValue(row, "lo");
			Integer hi = intValue(row, "hi");
			if (lo == null) {
				continue;
			}
			if (serviceYears < lo) {
				continue;
			}
			if (hi != null && serviceYears > hi) {
				continue;
			}
			matched = decimal(row, "pct");
		}
		return matched;
	}

	public BigDecimal computePeriodTax(ResolvedSurinameTaxRule rule, BigDecimal taxableBase, int periodCountPerYear) {
		if (rule == null || taxableBase == null || taxableBase.signum() <= 0) {
			return BigDecimal.ZERO.setScale(SCALE, ROUND);
		}
		JsonNode params = rule.parameters();
		if (params == null || params.isMissingNode()) {
			return BigDecimal.ZERO.setScale(SCALE, ROUND);
		}
		String kind = text(params, "kind");
		String freq = text(params, "freq");
		int periods = periodCountPerYear > 0 ? periodCountPerYear : DEFAULT_PERIODS_PER_YEAR;
		return switch (kind) {
			case "MARGINAL_RATES" -> computeMarginalPeriodTax(taxableBase, params, freq, periods);
			case "FLAT_RATE" -> computeFlatPeriodTax(taxableBase, params, freq, periods);
			default -> BigDecimal.ZERO.setScale(SCALE, ROUND);
		};
	}

	private BigDecimal computeMarginalPeriodTax(BigDecimal periodBase, JsonNode params, String freq, int periodsPerYear) {
		BigDecimal ladderBase = periodBase;
		if ("YEAR".equalsIgnoreCase(freq)) {
			ladderBase = periodBase.multiply(BigDecimal.valueOf(periodsPerYear));
		}
		BigDecimal annualOrPeriodTax = computeMarginalTaxOnBase(ladderBase, params);
		if ("YEAR".equalsIgnoreCase(freq)) {
			return annualOrPeriodTax.divide(BigDecimal.valueOf(periodsPerYear), SCALE, ROUND);
		}
		return annualOrPeriodTax.setScale(SCALE, ROUND);
	}

	private BigDecimal computeFlatPeriodTax(BigDecimal periodBase, JsonNode params, String freq, int periodsPerYear) {
		BigDecimal pct = decimal(params, "pct");
		if (pct == null || pct.signum() <= 0) {
			return BigDecimal.ZERO.setScale(SCALE, ROUND);
		}
		BigDecimal rate = pct.divide(BigDecimal.valueOf(100), SCALE + 4, ROUND);
		BigDecimal periodTax = periodBase.multiply(rate);
		if ("YEAR".equalsIgnoreCase(freq)) {
			periodTax = periodTax.divide(BigDecimal.valueOf(periodsPerYear), SCALE + 4, ROUND);
		}
		return periodTax.setScale(SCALE, ROUND);
	}

	BigDecimal computeMarginalTaxOnBase(BigDecimal taxableBase, JsonNode params) {
		if (taxableBase.signum() <= 0) {
			return BigDecimal.ZERO.setScale(SCALE, ROUND);
		}
		JsonNode rowsNode = params.get("rows");
		if (rowsNode == null || !rowsNode.isArray() || rowsNode.isEmpty()) {
			return BigDecimal.ZERO.setScale(SCALE, ROUND);
		}
		List<BracketRow> rows = new ArrayList<>();
		for (JsonNode row : rowsNode) {
			BigDecimal min = decimal(row, "min");
			if (min == null) {
				min = BigDecimal.ZERO;
			}
			BigDecimal max = decimal(row, "max");
			BigDecimal pct = decimal(row, "pct");
			if (pct == null) {
				continue;
			}
			rows.add(new BracketRow(min, max, pct));
		}
		rows.sort(Comparator.comparing(BracketRow::min));
		BigDecimal tax = BigDecimal.ZERO;
		for (BracketRow row : rows) {
			if (taxableBase.compareTo(row.min) <= 0) {
				continue;
			}
			BigDecimal upper = row.max != null ? taxableBase.min(row.max) : taxableBase;
			BigDecimal slice = upper.subtract(row.min);
			if (slice.signum() <= 0) {
				continue;
			}
			BigDecimal rate = row.pct.divide(BigDecimal.valueOf(100), SCALE + 4, ROUND);
			tax = tax.add(slice.multiply(rate));
		}
		return tax.setScale(SCALE, ROUND);
	}

	private static String text(JsonNode node, String field) {
		JsonNode v = node.get(field);
		return v != null && v.isTextual() ? v.asText() : "";
	}

	private static BigDecimal decimal(JsonNode node, String field) {
		JsonNode v = node.get(field);
		if (v == null || v.isNull()) {
			return null;
		}
		if (v.isNumber()) {
			return v.decimalValue();
		}
		if (v.isTextual()) {
			try {
				return new BigDecimal(v.asText().trim());
			}
			catch (NumberFormatException ex) {
				return null;
			}
		}
		return null;
	}

	private static Integer intValue(JsonNode node, String field) {
		JsonNode v = node.get(field);
		if (v == null || v.isNull()) {
			return null;
		}
		if (v.isNumber()) {
			return v.intValue();
		}
		if (v.isTextual()) {
			try {
				return Integer.parseInt(v.asText().trim());
			}
			catch (NumberFormatException ex) {
				return null;
			}
		}
		return null;
	}

	private record BracketRow(BigDecimal min, BigDecimal max, BigDecimal pct) {
	}
}
