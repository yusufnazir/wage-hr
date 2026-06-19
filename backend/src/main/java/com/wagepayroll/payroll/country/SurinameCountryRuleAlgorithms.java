package com.wagepayroll.payroll.country;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Suriname payroll algorithms driven by {@code platform_country_tax_rule} and wage-tax law
 * (progressive wage tax, belastingvrij threshold, deductible costs, benefits in kind).
 */
@Component
public class SurinameCountryRuleAlgorithms {

	public static final String LOONBELASTING_BASE = "LOONBELASTING";

	public static final String GROSS_BASE = "GROSS";

	private static final RoundingMode ROUND = RoundingMode.HALF_UP;

	private static final int SCALE = 4;

	private static final BigDecimal HUNDRED = new BigDecimal("100");

	/** Wage Tax Act 2024+: 4% of wages, max SRD 4,800 per year (FiscLe / tariff type 6). */
	private static final BigDecimal DEDUCTIBLE_PCT = new BigDecimal("4");

	private static final BigDecimal DEDUCTIBLE_ANNUAL_CAP = new BigDecimal("4800");

	/** Free medical care: 3% of yearly wage in money, max SRD 200 per year (FiscLe table). */
	private static final BigDecimal FREE_MEDICAL_PCT = new BigDecimal("3");

	private static final BigDecimal FREE_MEDICAL_ANNUAL_CAP = new BigDecimal("200");

	private static final MathContext MC = MathContext.DECIMAL64;

	/**
	 * Statutory belastingvrij allowance for the period (e.g. SRD 108 000/year ÷ 12), before capping to taxable income.
	 */
	/**
	 * Gross child allowance paid (component 1008): {@code children × perChild} per month.
	 * Not capped at {@code maxAmount} — that cap applies only to the Art. 10(h) loon-exclusion (see
	 * {@link #periodChildAllowanceExcludedFromLoon}).
	 */
	public BigDecimal periodChildAllowanceGrossAmount(SurinameTaxRulesSnapshot snapshot, BigDecimal childrenCount) {
		if (snapshot == null || childrenCount == null || childrenCount.signum() <= 0) {
			return zero();
		}
		ResolvedSurinameTaxRule rule = snapshot.rulesByCode().get(SurinameCountryRuleKeys.RULE_CHILD_ALLOWANCE_MONTH);
		PerChildMonthlyParams params = parsePerChildMonthlyParams(rule);
		if (params == null || params.perChild == null || params.perChild.signum() <= 0) {
			return zero();
		}
		int children = childrenCount.setScale(0, RoundingMode.DOWN).intValue();
		if (children <= 0) {
			return zero();
		}
		return params.perChild.multiply(BigDecimal.valueOf(children), MC).setScale(SCALE, ROUND);
	}

	/**
	 * Art. 10(h) Wet Loonbelasting: kinderbijslag excluded from wages up to SRD 125/child/month,
	 * maximum SRD 500/month in total ({@code maxAmount} on {@code SR_CHILD_ALLOWANCE_MONTH}).
	 */
	public BigDecimal periodChildAllowanceExcludedFromLoon(SurinameTaxRulesSnapshot snapshot,
			BigDecimal childrenCount) {
		BigDecimal gross = periodChildAllowanceGrossAmount(snapshot, childrenCount);
		if (gross.signum() <= 0) {
			return zero();
		}
		ResolvedSurinameTaxRule rule = snapshot.rulesByCode().get(SurinameCountryRuleKeys.RULE_CHILD_ALLOWANCE_MONTH);
		PerChildMonthlyParams params = parsePerChildMonthlyParams(rule);
		if (params == null || params.maxAmount == null) {
			return gross;
		}
		return gross.min(params.maxAmount).setScale(SCALE, ROUND);
	}

	/** Payslip line 1008 — same as {@link #periodChildAllowanceGrossAmount}. */
	public BigDecimal periodChildAllowanceAmount(SurinameTaxRulesSnapshot snapshot, BigDecimal childrenCount) {
		return periodChildAllowanceGrossAmount(snapshot, childrenCount);
	}

	public BigDecimal periodTaxFreeAllowance(SurinameTaxRulesSnapshot snapshot, boolean applyTaxExempt,
			int periodsPerYear) {
		if (!applyTaxExempt || snapshot == null) {
			return zero();
		}
		ResolvedSurinameTaxRule rule = snapshot.rulesByCode().get(SurinameCountryRuleKeys.RULE_TAX_FREE_WAGE_TAX_YEAR);
		return periodAmountFromThresholdRule(rule, periodsPerYear);
	}

	/**
	 * Belastingvrij applied this period: cannot exceed taxable income for the same period (payslip + wage-tax base).
	 */
	public BigDecimal periodTaxExemptApplied(SurinameTaxRulesSnapshot snapshot, boolean applyTaxExempt,
			int periodsPerYear, BigDecimal taxablePeriodBase) {
		if (!applyTaxExempt || snapshot == null) {
			return zero();
		}
		if (taxablePeriodBase == null || taxablePeriodBase.signum() <= 0) {
			return zero();
		}
		BigDecimal allowance = periodTaxFreeAllowance(snapshot, true, periodsPerYear);
		return allowance.min(taxablePeriodBase).setScale(SCALE, ROUND);
	}

	public BigDecimal periodDeductibleCosts(BigDecimal periodGrossBase, SurinameTaxRulesSnapshot snapshot,
			int periodsPerYear) {
		if (periodGrossBase == null || periodGrossBase.signum() <= 0) {
			return zero();
		}
		int periods = periodsPerYear > 0 ? periodsPerYear : SurinameWageTaxCalculator.DEFAULT_PERIODS_PER_YEAR;
		BigDecimal annualGross = periodGrossBase.multiply(BigDecimal.valueOf(periods));
		BigDecimal annualDeduction = annualGross.multiply(DEDUCTIBLE_PCT, MC)
				.divide(HUNDRED, SCALE + 4, ROUND);
		if (annualDeduction.compareTo(DEDUCTIBLE_ANNUAL_CAP) > 0) {
			annualDeduction = DEDUCTIBLE_ANNUAL_CAP;
		}
		return annualDeduction.divide(BigDecimal.valueOf(periods), SCALE, ROUND);
	}

	/**
	 * Taxable income for display (LOONBELASTING base before belastingvrij is applied in statutory phase).
	 */
	public BigDecimal taxableIncomeAmount(BigDecimal loonbelastingPeriodBase) {
		if (loonbelastingPeriodBase == null || loonbelastingPeriodBase.signum() <= 0) {
			return zero();
		}
		return loonbelastingPeriodBase.setScale(SCALE, ROUND);
	}

	/**
	 * Benefit-in-kind valuation for free medical care (not the 3% withholding ladder on tariff type 12).
	 */
	public BigDecimal periodFreeMedicalBenefit(BigDecimal periodTaxableWageMoney, int periodsPerYear) {
		if (periodTaxableWageMoney == null || periodTaxableWageMoney.signum() <= 0) {
			return zero();
		}
		int periods = periodsPerYear > 0 ? periodsPerYear : SurinameWageTaxCalculator.DEFAULT_PERIODS_PER_YEAR;
		BigDecimal annualWage = periodTaxableWageMoney.multiply(BigDecimal.valueOf(periods));
		BigDecimal annualBenefit = annualWage.multiply(FREE_MEDICAL_PCT, MC).divide(HUNDRED,
				SCALE + 4, ROUND);
		if (annualBenefit.compareTo(FREE_MEDICAL_ANNUAL_CAP) > 0) {
			annualBenefit = FREE_MEDICAL_ANNUAL_CAP;
		}
		return annualBenefit.divide(BigDecimal.valueOf(periods), SCALE, ROUND);
	}

	public BigDecimal adjustTaxableBaseForWageTax(BigDecimal loonbelastingPeriodBase, SurinameTaxRulesSnapshot snapshot,
			boolean applyTaxExempt, int periodsPerYear) {
		return adjustTaxableBaseForWageTax(loonbelastingPeriodBase, snapshot, applyTaxExempt, periodsPerYear, null, null);
	}

	public BigDecimal adjustTaxableBaseForWageTax(BigDecimal loonbelastingPeriodBase, SurinameTaxRulesSnapshot snapshot,
			boolean applyTaxExempt, int periodsPerYear, BigDecimal childAllowanceChildrenCount) {
		return adjustTaxableBaseForWageTax(loonbelastingPeriodBase, snapshot, applyTaxExempt, periodsPerYear,
				childAllowanceChildrenCount, null);
	}

	/**
	 * @param deductibleWageBase when non-null, forfaitaire beroepskosten (4%, max SRD 400/month) are subtracted after
	 *                           child exclusion and belastingvrij — same basis as payslip line 1036 ({@code GROSS} base).
	 */
	public BigDecimal adjustTaxableBaseForWageTax(BigDecimal loonbelastingPeriodBase, SurinameTaxRulesSnapshot snapshot,
			boolean applyTaxExempt, int periodsPerYear, BigDecimal childAllowanceChildrenCount,
			BigDecimal deductibleWageBase) {
		if (loonbelastingPeriodBase == null || loonbelastingPeriodBase.signum() <= 0) {
			return zero();
		}
		BigDecimal taxable = loonbelastingPeriodBase;
		if (childAllowanceChildrenCount != null && childAllowanceChildrenCount.signum() > 0) {
			taxable = taxable.subtract(
					periodChildAllowanceExcludedFromLoon(snapshot, childAllowanceChildrenCount));
		}
		if (applyTaxExempt) {
			taxable = taxable.subtract(periodTaxExemptApplied(snapshot, true, periodsPerYear, loonbelastingPeriodBase));
		}
		if (deductibleWageBase != null && deductibleWageBase.signum() > 0 && taxable.signum() > 0) {
			taxable = taxable.subtract(
					periodDeductibleCostsApplied(taxable, deductibleWageBase, snapshot, periodsPerYear));
		}
		if (taxable.signum() < 0) {
			return zero();
		}
		return taxable.setScale(SCALE, ROUND);
	}

	/** Beroepskosten applied this period, capped to remaining taxable base (matches template 1036 amount when fully applied). */
	public BigDecimal periodDeductibleCostsApplied(BigDecimal taxableAfterPriorAdjustments, BigDecimal deductibleWageBase,
			SurinameTaxRulesSnapshot snapshot, int periodsPerYear) {
		if (taxableAfterPriorAdjustments == null || taxableAfterPriorAdjustments.signum() <= 0
				|| deductibleWageBase == null || deductibleWageBase.signum() <= 0) {
			return zero();
		}
		BigDecimal deductible = periodDeductibleCosts(deductibleWageBase, snapshot, periodsPerYear);
		return deductible.min(taxableAfterPriorAdjustments).setScale(SCALE, ROUND);
	}

	private PerChildMonthlyParams parsePerChildMonthlyParams(ResolvedSurinameTaxRule rule) {
		if (rule == null || rule.parameters() == null || rule.parameters().isMissingNode()) {
			return null;
		}
		JsonNode params = rule.parameters();
		String kind = text(params, "kind");
		if ("PER_CHILD_MONTHLY".equals(kind)) {
			BigDecimal perChild = decimal(params, "perChild");
			BigDecimal maxAmount = decimal(params, "maxAmount");
			int maxChildren = intParam(params, "maxChildren", 4);
			if (perChild == null || perChild.signum() <= 0) {
				return null;
			}
			return new PerChildMonthlyParams(perChild, maxAmount, maxChildren);
		}
		if ("AMOUNT_BAND".equals(kind)) {
			BigDecimal perChild = decimal(params, "perChild");
			BigDecimal maxAmount = decimal(params, "max");
			if (perChild == null) {
				perChild = decimal(params, "min");
			}
			if (perChild == null || perChild.signum() <= 0) {
				return null;
			}
			return new PerChildMonthlyParams(perChild, maxAmount, intParam(params, "maxChildren", 4));
		}
		return null;
	}

	private static int intParam(JsonNode node, String field, int defaultValue) {
		JsonNode v = node.get(field);
		if (v == null || v.isNull()) {
			return defaultValue;
		}
		return v.isNumber() ? v.intValue() : defaultValue;
	}

	private record PerChildMonthlyParams(BigDecimal perChild, BigDecimal maxAmount, int maxChildren) {
	}

	private BigDecimal periodAmountFromThresholdRule(ResolvedSurinameTaxRule rule, int periodsPerYear) {
		if (rule == null || rule.parameters() == null || rule.parameters().isMissingNode()) {
			return zero();
		}
		JsonNode params = rule.parameters();
		if (!"THRESHOLD_AMOUNT".equals(text(params, "kind"))) {
			return zero();
		}
		BigDecimal amount = decimal(params, "amount");
		if (amount == null || amount.signum() <= 0) {
			return zero();
		}
		int periods = periodsPerYear > 0 ? periodsPerYear : SurinameWageTaxCalculator.DEFAULT_PERIODS_PER_YEAR;
		if ("YEAR".equalsIgnoreCase(text(params, "freq"))) {
			return amount.divide(BigDecimal.valueOf(periods), SCALE, ROUND);
		}
		return amount.setScale(SCALE, ROUND);
	}

	private static BigDecimal zero() {
		return BigDecimal.ZERO.setScale(SCALE, ROUND);
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

}
