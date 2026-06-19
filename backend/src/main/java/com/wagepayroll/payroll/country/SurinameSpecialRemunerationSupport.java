package com.wagepayroll.payroll.country;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.wagepayroll.payroll.engine.EvaluatedComponentAmount;

/**
 * Art. 10 (exempt cap) and art. 17 inputs for vacation allowance (1006), bonus (1007),
 * and extra earnings (1011 — fully taxable, no art. 10 cap).
 */
public final class SurinameSpecialRemunerationSupport {

	public static final String VACATION_COMPONENT_CODE = "1006";

	public static final String BONUS_COMPONENT_CODE = "1007";

	public static final String EXTRA_EARNINGS_COMPONENT_CODE = "1011";

	public static final String LUMP_SUM_COMPONENT_CODE = "1009";

	/** Overtime earning templates (1045–1047). */
	public static final Set<String> OVERTIME_COMPONENT_CODES = Set.of("1045", "1046", "1047");

	/** Normal pay periods a full-year vacation/bonus payment is attributed to (art. 17 lid 2e). */
	public static final int DEFAULT_ATTRIBUTION_PERIODS = 12;

	private static final RoundingMode ROUND = RoundingMode.HALF_UP;

	private static final int SCALE = 4;

	private SurinameSpecialRemunerationSupport() {
	}

	public record Amounts(
			BigDecimal vacationPayout,
			BigDecimal bonusPayout,
			BigDecimal overtimePayout,
			BigDecimal lumpSumPayout,
			BigDecimal jubileePayout,
			BigDecimal extraEarningsPayout,
			BigDecimal vacationExempt,
			BigDecimal vacationTaxable,
			BigDecimal bonusExempt,
			BigDecimal bonusTaxable,
			BigDecimal jubileeExempt,
			BigDecimal jubileeTaxable,
			Integer serviceYears,
			BigDecimal extraEarningsTaxable,
			BigDecimal labelPeriodWage) {
	}

	public static Amounts compute(BigDecimal loonbelastingBase, BigDecimal vacationPayout, BigDecimal bonusPayout,
			BigDecimal overtimePayout, BigDecimal lumpSumPayout, BigDecimal jubileePayout,
			BigDecimal extraEarningsPayout, BigDecimal referenceMonthWage, Integer serviceYears,
			SurinameTaxRulesSnapshot snapshot, int periodsPerYear) {
		BigDecimal vacation = nz(vacationPayout);
		BigDecimal bonus = nz(bonusPayout);
		BigDecimal overtime = nz(overtimePayout);
		BigDecimal lumpSum = nz(lumpSumPayout);
		BigDecimal jubilee = nz(jubileePayout);
		BigDecimal extraEarnings = nz(extraEarningsPayout);
		BigDecimal monthRef = referenceMonthWage != null && referenceMonthWage.signum() > 0
				? referenceMonthWage.setScale(SCALE, ROUND)
				: zero();
		BigDecimal vacationExempt = exemptPortion(vacation, monthRef, snapshot,
				SurinameCountryRuleKeys.RULE_TAX_FREE_VACATION_YEAR, periodsPerYear);
		BigDecimal bonusExempt = exemptPortion(bonus, monthRef, snapshot,
				SurinameCountryRuleKeys.RULE_TAX_FREE_BONUS_YEAR, periodsPerYear);
		BigDecimal vacationTaxable = vacation.subtract(vacationExempt).max(zero());
		BigDecimal bonusTaxable = bonus.subtract(bonusExempt).max(zero());
		BigDecimal extraEarningsTaxable = extraEarnings;
		SurinameJubileeSupport.JubileeAmounts jubileeAmounts = SurinameJubileeSupport
				.computeJubileeAmounts(jubilee, referenceMonthWage, serviceYears);
		BigDecimal label = nz(loonbelastingBase).subtract(vacation).subtract(bonus).subtract(overtime).subtract(lumpSum)
				.subtract(jubilee).subtract(extraEarnings);
		if (label.signum() < 0) {
			label = zero();
		}
		return new Amounts(vacation, bonus, overtime, lumpSum, jubilee, extraEarnings, vacationExempt, vacationTaxable,
				bonusExempt, bonusTaxable, jubileeAmounts.exempt(), jubileeAmounts.taxable(), serviceYears,
				extraEarningsTaxable, label.setScale(SCALE, ROUND));
	}

	public static BigDecimal overtimePayoutForEmployee(List<EvaluatedComponentAmount> passOneLines, UUID employeeId) {
		BigDecimal total = zero();
		for (String code : OVERTIME_COMPONENT_CODES) {
			total = total.add(payoutForCode(passOneLines, employeeId, code));
		}
		return total.setScale(SCALE, ROUND);
	}

	/**
	 * Exempt slice: min(payout, one month reference wage, annual rule threshold for the payout period).
	 */
	static BigDecimal exemptPortion(BigDecimal payout, BigDecimal referenceMonthWage, SurinameTaxRulesSnapshot snapshot,
			String ruleCode, int periodsPerYear) {
		if (payout.signum() <= 0) {
			return zero();
		}
		BigDecimal cap = referenceMonthWage;
		if (snapshot != null && ruleCode != null) {
			ResolvedSurinameTaxRule rule = snapshot.rulesByCode().get(ruleCode);
			BigDecimal annualThreshold = annualThresholdAmount(rule);
			if (annualThreshold.signum() > 0) {
				cap = cap.min(annualThreshold);
			}
		}
		return payout.min(cap).setScale(SCALE, ROUND);
	}

	static BigDecimal annualThresholdAmount(ResolvedSurinameTaxRule rule) {
		if (rule == null || rule.parameters() == null || rule.parameters().isMissingNode()) {
			return zero();
		}
		var params = rule.parameters();
		if (!"THRESHOLD_AMOUNT".equals(text(params, "kind"))) {
			return zero();
		}
		var amount = decimal(params, "amount");
		return amount != null ? amount.setScale(SCALE, ROUND) : zero();
	}

	public static BigDecimal payoutForCode(List<EvaluatedComponentAmount> passOneLines, UUID employeeId, String code) {
		return passOneLines.stream()
				.filter(line -> employeeId.equals(line.employeeId()) && code.equals(line.tenantWageComponentCode()))
				.map(EvaluatedComponentAmount::evaluatedAmount)
				.filter(amount -> amount != null && amount.signum() > 0)
				.reduce(BigDecimal.ZERO, BigDecimal::add)
				.setScale(SCALE, ROUND);
	}

	public static Map<UUID, Amounts> amountsByEmployee(List<UUID> employeeIds, List<EvaluatedComponentAmount> passOneLines,
			Map<UUID, Map<String, BigDecimal>> baseTotals, Map<UUID, BigDecimal> referenceMonthWageByEmployee,
			Map<UUID, Integer> serviceYearsByEmployee, SurinameTaxRulesSnapshot snapshot, int periodsPerYear) {
		Map<UUID, Integer> serviceYearsLookup = serviceYearsByEmployee != null ? serviceYearsByEmployee : Map.of();
		Map<UUID, Amounts> out = new java.util.HashMap<>();
		for (UUID employeeId : employeeIds) {
			Map<String, BigDecimal> bases = baseTotals.getOrDefault(employeeId, Map.of());
			BigDecimal loonbelasting = bases.getOrDefault(SurinameCountryRuleAlgorithms.LOONBELASTING_BASE,
					BigDecimal.ZERO);
			BigDecimal vacation = payoutForCode(passOneLines, employeeId, VACATION_COMPONENT_CODE);
			BigDecimal bonus = payoutForCode(passOneLines, employeeId, BONUS_COMPONENT_CODE);
			BigDecimal overtime = overtimePayoutForEmployee(passOneLines, employeeId);
			BigDecimal lumpSum = payoutForCode(passOneLines, employeeId, LUMP_SUM_COMPONENT_CODE);
			BigDecimal jubilee = payoutForCode(passOneLines, employeeId, SurinameJubileeSupport.JUBILEE_COMPONENT_CODE);
			BigDecimal extraEarnings = payoutForCode(passOneLines, employeeId, EXTRA_EARNINGS_COMPONENT_CODE);
			BigDecimal ref = referenceMonthWageByEmployee.getOrDefault(employeeId, zero());
			Integer serviceYears = serviceYearsLookup.get(employeeId);
			out.put(employeeId, compute(loonbelasting, vacation, bonus, overtime, lumpSum, jubilee, extraEarnings, ref,
					serviceYears, snapshot, periodsPerYear));
		}
		return out;
	}

	private static BigDecimal nz(BigDecimal value) {
		return value != null ? value.setScale(SCALE, ROUND) : zero();
	}

	private static BigDecimal zero() {
		return BigDecimal.ZERO.setScale(SCALE, ROUND);
	}

	private static String text(com.fasterxml.jackson.databind.JsonNode node, String field) {
		com.fasterxml.jackson.databind.JsonNode v = node.get(field);
		return v != null && v.isTextual() ? v.asText() : "";
	}

	private static BigDecimal decimal(com.fasterxml.jackson.databind.JsonNode node, String field) {
		com.fasterxml.jackson.databind.JsonNode v = node.get(field);
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
}
