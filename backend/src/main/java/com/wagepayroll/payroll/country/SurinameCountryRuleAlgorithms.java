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

	/** Free company car: minimum 2% of list price per year (FiscLe / SR_COMPANY_CAR_YEAR). */
	private static final BigDecimal COMPANY_CAR_PCT = new BigDecimal("2");

	/** Free housing: 7.5% of annual money wage (FiscLe / SR_FREE_HOUSING_YEAR). */
	private static final BigDecimal FREE_HOUSING_PCT = new BigDecimal("7.5");

	private static final BigDecimal BOARD_LODGING_DAY_CAP = new BigDecimal("10");

	private static final BigDecimal BOARD_DAY_CAP = new BigDecimal("5");

	private static final BigDecimal HOT_MEAL_UNIT_CAP = new BigDecimal("5");

	private static final BigDecimal BREAD_MEAL_UNIT_CAP = new BigDecimal("1.50");

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

	/**
	 * Gross exchange-rate compensation paid (component 1055): full period payout from employer-entered amount.
	 */
	public BigDecimal periodExchangeRateCompensationPayout(BigDecimal payout) {
		if (payout == null || payout.signum() <= 0) {
			return zero();
		}
		return payout.setScale(SCALE, ROUND);
	}

	/**
	 * Art. 10 Wet Loonbelasting: exchange-rate compensation excluded from wages up to SRD 800/month
	 * ({@code maxAmount} on {@code SR_EXCHANGE_RATE_COMPENSATION_MONTH}).
	 */
	public BigDecimal periodExchangeRateCompensationExcludedFromLoon(SurinameTaxRulesSnapshot snapshot,
			BigDecimal payout) {
		BigDecimal gross = periodExchangeRateCompensationPayout(payout);
		if (gross.signum() <= 0) {
			return zero();
		}
		ResolvedSurinameTaxRule rule = snapshot.rulesByCode()
				.get(SurinameCountryRuleKeys.RULE_EXCHANGE_RATE_COMPENSATION_MONTH);
		BigDecimal maxAmount = monthlyCapFromThresholdRule(rule);
		if (maxAmount == null) {
			return gross;
		}
		return gross.min(maxAmount).setScale(SCALE, ROUND);
	}

	/**
	 * Gross pension-scheme payout (component 1064): full period payout from employer-entered amount.
	 */
	public BigDecimal periodPensionSchemePayout(BigDecimal payout) {
		if (payout == null || payout.signum() <= 0) {
			return zero();
		}
		return payout.setScale(SCALE, ROUND);
	}

	/**
	 * Art. 10(k): pension-scheme payout excluded from wages up to 2× monthly AOV beneficiary amount
	 * ({@code SR_AOV_BENEFICIARY_MONTH}).
	 */
	public BigDecimal periodPension2xAovExcludedFromLoon(SurinameTaxRulesSnapshot snapshot, BigDecimal payout) {
		BigDecimal gross = periodPensionSchemePayout(payout);
		if (gross.signum() <= 0) {
			return zero();
		}
		BigDecimal monthlyCap = pension2xAovMonthlyExclusionCap(snapshot);
		if (monthlyCap == null) {
			return gross;
		}
		return gross.min(monthlyCap).setScale(SCALE, ROUND);
	}

	private BigDecimal pension2xAovMonthlyExclusionCap(SurinameTaxRulesSnapshot snapshot) {
		if (snapshot == null) {
			return null;
		}
		ResolvedSurinameTaxRule rule = snapshot.rulesByCode().get(SurinameCountryRuleKeys.RULE_AOV_BENEFICIARY_MONTH);
		BigDecimal monthlyAov = monthlyCapFromThresholdRule(rule);
		if (monthlyAov == null || monthlyAov.signum() <= 0) {
			return null;
		}
		return monthlyAov.multiply(BigDecimal.valueOf(2), MC).setScale(SCALE, ROUND);
	}

	/** Gross cost allowance paid (component 1058): full period payout from employer-entered amount. */
	public BigDecimal periodCostAllowancePayout(BigDecimal payout) {
		return periodPensionSchemePayout(payout);
	}

	/** Art. 10(e): full entered cost allowance amount excluded from wages (no statutory cap v1). */
	public BigDecimal periodCostAllowanceExcludedFromLoon(BigDecimal payout) {
		return periodCostAllowancePayout(payout);
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
		return periodPercentOfAnnualMoneyWage(periodTaxableWageMoney, periodsPerYear, FREE_MEDICAL_PCT,
				FREE_MEDICAL_ANNUAL_CAP);
	}

	/**
	 * Pre–benefit-in-kind money wage base: LOONBELASTING subtotal before derived valuation lines
	 * (1042, 1049–1057) are applied in the tax-adjustment tier.
	 */
	public BigDecimal moneyWageBasePreBenefitInKind(BigDecimal loonbelastingPeriodBase) {
		if (loonbelastingPeriodBase == null || loonbelastingPeriodBase.signum() <= 0) {
			return zero();
		}
		return loonbelastingPeriodBase.setScale(SCALE, ROUND);
	}

	/**
	 * Art. 10 company car — minimum 2% of list price per year ÷ periods ({@code SR_COMPANY_CAR_YEAR}).
	 */
	public BigDecimal periodCompanyCarBenefit(BigDecimal listPrice, SurinameTaxRulesSnapshot snapshot, int periodsPerYear) {
		if (listPrice == null || listPrice.signum() <= 0) {
			return zero();
		}
		BigDecimal pct = pctFromFlatRateRule(snapshot, SurinameCountryRuleKeys.RULE_COMPANY_CAR_YEAR, COMPANY_CAR_PCT);
		int periods = periodsPerYear > 0 ? periodsPerYear : SurinameWageTaxCalculator.DEFAULT_PERIODS_PER_YEAR;
		BigDecimal annualValuation = listPrice.multiply(pct, MC).divide(HUNDRED, SCALE + 4, ROUND);
		return annualValuation.divide(BigDecimal.valueOf(periods), SCALE, ROUND);
	}

	/**
	 * Art. 10 free housing — 7.5% of annual money wage ÷ periods ({@code SR_FREE_HOUSING_YEAR}).
	 */
	public BigDecimal periodFreeHousingBenefit(BigDecimal moneyWagePeriodBase, SurinameTaxRulesSnapshot snapshot,
			int periodsPerYear) {
		BigDecimal moneyWage = moneyWageBasePreBenefitInKind(moneyWagePeriodBase);
		if (moneyWage.signum() <= 0) {
			return zero();
		}
		BigDecimal pct = pctFromFlatRateRule(snapshot, SurinameCountryRuleKeys.RULE_FREE_HOUSING_YEAR, FREE_HOUSING_PCT);
		int periods = periodsPerYear > 0 ? periodsPerYear : SurinameWageTaxCalculator.DEFAULT_PERIODS_PER_YEAR;
		BigDecimal annualWage = moneyWage.multiply(BigDecimal.valueOf(periods));
		BigDecimal annualValuation = annualWage.multiply(pct, MC).divide(HUNDRED, SCALE + 4, ROUND);
		return annualValuation.divide(BigDecimal.valueOf(periods), SCALE, ROUND);
	}

	/** Art. 10 board and lodging — quantity (days) × SRD 10/day ({@code SR_BOARD_LODGING_DAY}). */
	public BigDecimal periodBoardLodgingBenefit(BigDecimal quantity, SurinameTaxRulesSnapshot snapshot) {
		return periodQuantityCapBenefit(quantity, snapshot, SurinameCountryRuleKeys.RULE_BOARD_LODGING_DAY,
				BOARD_LODGING_DAY_CAP);
	}

	/** Art. 10 board only — quantity (days) × SRD 5/day ({@code SR_BOARD_DAY}). */
	public BigDecimal periodBoardBenefit(BigDecimal quantity, SurinameTaxRulesSnapshot snapshot) {
		return periodQuantityCapBenefit(quantity, snapshot, SurinameCountryRuleKeys.RULE_BOARD_DAY, BOARD_DAY_CAP);
	}

	/** Art. 10 hot meal — quantity (meals) × SRD 5/meal ({@code SR_HOT_MEAL_UNIT}). */
	public BigDecimal periodHotMealBenefit(BigDecimal quantity, SurinameTaxRulesSnapshot snapshot) {
		return periodQuantityCapBenefit(quantity, snapshot, SurinameCountryRuleKeys.RULE_HOT_MEAL_UNIT,
				HOT_MEAL_UNIT_CAP);
	}

	/** Art. 10 bread meal — quantity (meals) × SRD 1.50/meal ({@code SR_BREAD_MEAL_UNIT}). */
	public BigDecimal periodBreadMealBenefit(BigDecimal quantity, SurinameTaxRulesSnapshot snapshot) {
		return periodQuantityCapBenefit(quantity, snapshot, SurinameCountryRuleKeys.RULE_BREAD_MEAL_UNIT,
				BREAD_MEAL_UNIT_CAP);
	}

	/**
	 * Art. 10 free utilities — chargeable utility cost entered by employer (component 1057).
	 */
	public BigDecimal periodFreeUtilitiesBenefit(BigDecimal chargeableAmount) {
		if (chargeableAmount == null || chargeableAmount.signum() <= 0) {
			return zero();
		}
		return chargeableAmount.setScale(SCALE, ROUND);
	}

	/**
	 * Quantity-driven benefit valuation: {@code periodValuation = quantity × statutory unit cap}.
	 */
	public BigDecimal periodQuantityCapBenefit(BigDecimal quantity, SurinameTaxRulesSnapshot snapshot, String ruleCode,
			BigDecimal defaultCap) {
		if (quantity == null || quantity.signum() <= 0) {
			return zero();
		}
		BigDecimal cap = unitCapFromRule(snapshot, ruleCode, defaultCap);
		if (cap == null || cap.signum() <= 0) {
			return zero();
		}
		return quantity.multiply(cap, MC).setScale(SCALE, ROUND);
	}

	private BigDecimal periodPercentOfAnnualMoneyWage(BigDecimal periodTaxableWageMoney, int periodsPerYear,
			BigDecimal pct, BigDecimal annualCap) {
		if (periodTaxableWageMoney == null || periodTaxableWageMoney.signum() <= 0) {
			return zero();
		}
		int periods = periodsPerYear > 0 ? periodsPerYear : SurinameWageTaxCalculator.DEFAULT_PERIODS_PER_YEAR;
		BigDecimal annualWage = periodTaxableWageMoney.multiply(BigDecimal.valueOf(periods));
		BigDecimal annualBenefit = annualWage.multiply(pct, MC).divide(HUNDRED, SCALE + 4, ROUND);
		if (annualCap != null && annualBenefit.compareTo(annualCap) > 0) {
			annualBenefit = annualCap;
		}
		return annualBenefit.divide(BigDecimal.valueOf(periods), SCALE, ROUND);
	}

	private BigDecimal unitCapFromRule(SurinameTaxRulesSnapshot snapshot, String ruleCode, BigDecimal defaultCap) {
		if (snapshot == null || ruleCode == null) {
			return defaultCap;
		}
		ResolvedSurinameTaxRule rule = snapshot.rulesByCode().get(ruleCode);
		if (rule == null || rule.parameters() == null || rule.parameters().isMissingNode()) {
			return defaultCap;
		}
		JsonNode params = rule.parameters();
		String kind = text(params, "kind");
		if ("UNIT_CAP".equals(kind) || "THRESHOLD_AMOUNT".equals(kind)) {
			BigDecimal amount = decimal(params, "amount");
			return amount != null && amount.signum() > 0 ? amount : defaultCap;
		}
		return defaultCap;
	}

	private BigDecimal pctFromFlatRateRule(SurinameTaxRulesSnapshot snapshot, String ruleCode, BigDecimal defaultPct) {
		if (snapshot == null || ruleCode == null) {
			return defaultPct;
		}
		ResolvedSurinameTaxRule rule = snapshot.rulesByCode().get(ruleCode);
		if (rule == null || rule.parameters() == null || rule.parameters().isMissingNode()) {
			return defaultPct;
		}
		JsonNode params = rule.parameters();
		if (!"FLAT_RATE".equals(text(params, "kind"))) {
			return defaultPct;
		}
		BigDecimal pct = decimal(params, "pct");
		return pct != null && pct.signum() > 0 ? pct : defaultPct;
	}

	public BigDecimal adjustTaxableBaseForWageTax(BigDecimal loonbelastingPeriodBase, SurinameTaxRulesSnapshot snapshot,
			boolean applyTaxExempt, int periodsPerYear) {
		return adjustTaxableBaseForWageTax(loonbelastingPeriodBase, snapshot, applyTaxExempt, periodsPerYear, null, null,
				null, null, null);
	}

	public BigDecimal adjustTaxableBaseForWageTax(BigDecimal loonbelastingPeriodBase, SurinameTaxRulesSnapshot snapshot,
			boolean applyTaxExempt, int periodsPerYear, BigDecimal childAllowanceChildrenCount) {
		return adjustTaxableBaseForWageTax(loonbelastingPeriodBase, snapshot, applyTaxExempt, periodsPerYear,
				childAllowanceChildrenCount, null, null, null, null);
	}

	public BigDecimal adjustTaxableBaseForWageTax(BigDecimal loonbelastingPeriodBase, SurinameTaxRulesSnapshot snapshot,
			boolean applyTaxExempt, int periodsPerYear, BigDecimal childAllowanceChildrenCount,
			BigDecimal deductibleWageBase) {
		return adjustTaxableBaseForWageTax(loonbelastingPeriodBase, snapshot, applyTaxExempt, periodsPerYear,
				childAllowanceChildrenCount, deductibleWageBase, null, null, null);
	}

	public BigDecimal adjustTaxableBaseForWageTax(BigDecimal loonbelastingPeriodBase, SurinameTaxRulesSnapshot snapshot,
			boolean applyTaxExempt, int periodsPerYear, BigDecimal childAllowanceChildrenCount,
			BigDecimal deductibleWageBase, BigDecimal exchangeRatePayout) {
		return adjustTaxableBaseForWageTax(loonbelastingPeriodBase, snapshot, applyTaxExempt, periodsPerYear,
				childAllowanceChildrenCount, deductibleWageBase, exchangeRatePayout, null, null);
	}

	public BigDecimal adjustTaxableBaseForWageTax(BigDecimal loonbelastingPeriodBase, SurinameTaxRulesSnapshot snapshot,
			boolean applyTaxExempt, int periodsPerYear, BigDecimal childAllowanceChildrenCount,
			BigDecimal deductibleWageBase, BigDecimal exchangeRatePayout, BigDecimal pensionSchemePayout) {
		return adjustTaxableBaseForWageTax(loonbelastingPeriodBase, snapshot, applyTaxExempt, periodsPerYear,
				childAllowanceChildrenCount, deductibleWageBase, exchangeRatePayout, pensionSchemePayout, null);
	}

	/**
	 * @param deductibleWageBase when non-null, forfaitaire beroepskosten (4%, max SRD 400/month) are subtracted after
	 *                           child exclusion and belastingvrij — same basis as payslip line 1036 ({@code GROSS} base).
	 * @param exchangeRatePayout when non-null, Art. 10 exchange-rate exclusion (1056) is subtracted after child
	 *                           exclusion.
	 * @param pensionSchemePayout when non-null, Art. 10(k) pension exclusion (1065) is subtracted after exchange
	 *                            exclusion.
	 * @param costAllowancePayout when non-null, Art. 10(e) cost allowance exclusion (1059) is subtracted after pension
	 *                            exclusion.
	 */
	public BigDecimal adjustTaxableBaseForWageTax(BigDecimal loonbelastingPeriodBase, SurinameTaxRulesSnapshot snapshot,
			boolean applyTaxExempt, int periodsPerYear, BigDecimal childAllowanceChildrenCount,
			BigDecimal deductibleWageBase, BigDecimal exchangeRatePayout, BigDecimal pensionSchemePayout,
			BigDecimal costAllowancePayout) {
		if (loonbelastingPeriodBase == null || loonbelastingPeriodBase.signum() <= 0) {
			return zero();
		}
		BigDecimal taxable = loonbelastingPeriodBase;
		if (childAllowanceChildrenCount != null && childAllowanceChildrenCount.signum() > 0) {
			taxable = taxable.subtract(
					periodChildAllowanceExcludedFromLoon(snapshot, childAllowanceChildrenCount));
		}
		if (exchangeRatePayout != null && exchangeRatePayout.signum() > 0) {
			taxable = taxable.subtract(
					periodExchangeRateCompensationExcludedFromLoon(snapshot, exchangeRatePayout));
		}
		if (pensionSchemePayout != null && pensionSchemePayout.signum() > 0) {
			taxable = taxable.subtract(periodPension2xAovExcludedFromLoon(snapshot, pensionSchemePayout));
		}
		if (costAllowancePayout != null && costAllowancePayout.signum() > 0) {
			taxable = taxable.subtract(periodCostAllowanceExcludedFromLoon(costAllowancePayout));
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

	private BigDecimal monthlyCapFromThresholdRule(ResolvedSurinameTaxRule rule) {
		if (rule == null || rule.parameters() == null || rule.parameters().isMissingNode()) {
			return null;
		}
		JsonNode params = rule.parameters();
		if (!"THRESHOLD_AMOUNT".equals(text(params, "kind"))) {
			return null;
		}
		BigDecimal amount = decimal(params, "amount");
		if (amount == null || amount.signum() <= 0) {
			return null;
		}
		if ("YEAR".equalsIgnoreCase(text(params, "freq"))) {
			return amount.divide(BigDecimal.valueOf(SurinameWageTaxCalculator.DEFAULT_PERIODS_PER_YEAR), SCALE, ROUND);
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
