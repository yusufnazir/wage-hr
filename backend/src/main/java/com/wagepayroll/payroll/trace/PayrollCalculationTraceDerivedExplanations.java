package com.wagepayroll.payroll.trace;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.wagepayroll.payroll.base.PayrollBaseContribution;
import com.wagepayroll.payroll.country.SurinameCountryRuleAlgorithms;
import com.wagepayroll.payroll.country.SurinameCountryRuleKeys;
import com.wagepayroll.payroll.country.SurinameSpecialRemunerationSupport;

public final class PayrollCalculationTraceDerivedExplanations {

	private PayrollCalculationTraceDerivedExplanations() {
	}

	public static String factorForCountryRule(String countryRuleKey, BigDecimal payrollInputQuantity) {
		if (SurinameCountryRuleKeys.CHILD_ALLOWANCE.equals(countryRuleKey)
				|| SurinameCountryRuleKeys.WAGE_TAX_CHILD_ALLOWANCE.equals(countryRuleKey)) {
			return payrollInputQuantity != null
					? "Children count from standing instruction (component 1008 quantity): "
							+ PayrollCalculationTraceSupport.formatMoney(payrollInputQuantity)
					: "Children count from standing instruction (component 1008 quantity).";
		}
		return "Derived by country payroll algorithm (no quantity × rate).";
	}

	public static String amountForCountryRule(String countryRuleKey, BigDecimal loonbelasting, BigDecimal gross,
			SurinameSpecialRemunerationSupport.Amounts special, BigDecimal payrollInputQuantity, BigDecimal amount) {
		return amountForCountryRule(countryRuleKey, loonbelasting, gross, special, payrollInputQuantity, amount,
				Map.of());
	}

	public static String amountForCountryRule(String countryRuleKey, BigDecimal loonbelasting, BigDecimal gross,
			SurinameSpecialRemunerationSupport.Amounts special, BigDecimal payrollInputQuantity, BigDecimal amount,
			Map<String, List<PayrollBaseContribution>> contributionsByBase) {
		String formatted = PayrollCalculationTraceSupport.formatMoney(amount);
		return switch (countryRuleKey) {
			case SurinameCountryRuleKeys.TAXABLE_INCOME -> PayrollCalculationTraceSupport.appendBreakdown(
					"Taxable income line = LOONBELASTING base "
							+ PayrollCalculationTraceSupport.formatMoney(loonbelasting) + " → " + formatted,
					PayrollCalculationTraceSupport.formatBaseBreakdownFromMap(contributionsByBase,
							SurinameCountryRuleAlgorithms.LOONBELASTING_BASE, loonbelasting));
			case SurinameCountryRuleKeys.TAX_FREE_WAGE_TAX -> PayrollCalculationTraceSupport.appendBreakdown(
					"Belastingvrij applied this period (capped to LOONBELASTING base) → " + formatted,
					PayrollCalculationTraceSupport.formatBaseBreakdownFromMap(contributionsByBase,
							SurinameCountryRuleAlgorithms.LOONBELASTING_BASE, loonbelasting));
			case SurinameCountryRuleKeys.ACQUISITION_COSTS -> PayrollCalculationTraceSupport.appendBreakdown(
					"Deductible costs (4% of gross, annual cap) from gross "
							+ PayrollCalculationTraceSupport.formatMoney(gross) + " → " + formatted,
					PayrollCalculationTraceSupport.formatBaseBreakdownFromMap(contributionsByBase,
							SurinameCountryRuleAlgorithms.GROSS_BASE, gross));
			case SurinameCountryRuleKeys.FREE_MEDICAL_BENEFIT -> PayrollCalculationTraceSupport.appendBreakdown(
					"Free medical benefit valuation from LOONBELASTING base → " + formatted,
					PayrollCalculationTraceSupport.formatBaseBreakdownFromMap(contributionsByBase,
							SurinameCountryRuleAlgorithms.LOONBELASTING_BASE, loonbelasting));
			case SurinameCountryRuleKeys.CHILD_ALLOWANCE ->
				"Gross child allowance = children × statutory per-child rate → " + formatted;
			case SurinameCountryRuleKeys.WAGE_TAX_CHILD_ALLOWANCE ->
				"Art. 10(h) child allowance excluded from wages (capped) → " + formatted;
			case SurinameCountryRuleKeys.WAGE_TAX_VACATION_ALLOWANCE -> PayrollCalculationTraceSupport.appendBreakdown(
					"Art. 17 wage tax on taxable vacation portion "
							+ PayrollCalculationTraceSupport.formatMoney(special != null ? special.vacationTaxable() : null)
							+ ", label wage "
							+ PayrollCalculationTraceSupport.formatMoney(special != null ? special.labelPeriodWage() : null)
							+ " → " + formatted,
					PayrollCalculationTraceSupport.formatSpecialPayoutSource(
							SurinameSpecialRemunerationSupport.VACATION_COMPONENT_CODE, "vacation payout",
							special != null ? special.vacationPayout() : null,
							special != null ? special.vacationExempt() : null,
							special != null ? special.vacationTaxable() : null));
			case SurinameCountryRuleKeys.WAGE_TAX_BONUS -> PayrollCalculationTraceSupport.appendBreakdown(
					"Art. 17 wage tax on taxable bonus "
							+ PayrollCalculationTraceSupport.formatMoney(special != null ? special.bonusTaxable() : null)
							+ " → " + formatted,
					PayrollCalculationTraceSupport.formatSpecialPayoutSource(
							SurinameSpecialRemunerationSupport.BONUS_COMPONENT_CODE, "bonus payout",
							special != null ? special.bonusPayout() : null, special != null ? special.bonusExempt() : null,
							special != null ? special.bonusTaxable() : null));
			case SurinameCountryRuleKeys.WAGE_TAX_EXTRA_EARNINGS -> PayrollCalculationTraceSupport.appendBreakdown(
					"Art. 17 wage tax on extra earnings (fully taxable) "
							+ PayrollCalculationTraceSupport
									.formatMoney(special != null ? special.extraEarningsTaxable() : null)
							+ " → " + formatted,
					PayrollCalculationTraceSupport.formatSpecialPayoutSource(
							SurinameSpecialRemunerationSupport.EXTRA_EARNINGS_COMPONENT_CODE, "extra earnings payout",
							special != null ? special.extraEarningsPayout() : null, null,
							special != null ? special.extraEarningsTaxable() : null));
			case SurinameCountryRuleKeys.WAGE_TAX_OVERTIME -> PayrollCalculationTraceSupport.appendBreakdown(
					"Overtime wage tax (SR_OVERTIME_MONTH brackets) on overtime payout "
							+ PayrollCalculationTraceSupport.formatMoney(special != null ? special.overtimePayout() : null)
							+ " → " + formatted,
					special != null && special.overtimePayout() != null && special.overtimePayout().signum() > 0
							? "Source: [1045–1047] overtime payout "
									+ PayrollCalculationTraceSupport.formatMoney(special.overtimePayout())
							: "");
			case SurinameCountryRuleKeys.WAGE_TAX_LUMP_SUM -> PayrollCalculationTraceSupport.appendBreakdown(
					"Lump-sum wage tax (SR_PAYMENTS_AT_ONCE_YEAR brackets) on payout "
							+ PayrollCalculationTraceSupport.formatMoney(special != null ? special.lumpSumPayout() : null)
							+ " → " + formatted,
					special != null && special.lumpSumPayout() != null && special.lumpSumPayout().signum() > 0
							? "Source: [1009] lump sum payout "
									+ PayrollCalculationTraceSupport.formatMoney(special.lumpSumPayout())
							: "");
			case SurinameCountryRuleKeys.WAGE_TAX_JUBILEE -> PayrollCalculationTraceSupport.appendBreakdown(
					"Jubilee wage tax (SR_SERVICE_YEARS_17A_MONTH) on taxable portion "
							+ PayrollCalculationTraceSupport.formatMoney(special != null ? special.jubileeTaxable() : null)
							+ ", service years " + (special != null ? special.serviceYears() : null) + " → " + formatted,
					special != null && special.jubileePayout() != null && special.jubileePayout().signum() > 0
							? "Source: [1010] jubilee payout "
									+ PayrollCalculationTraceSupport.formatMoney(special.jubileePayout())
									+ "; Art. 10 exempt "
									+ PayrollCalculationTraceSupport.formatMoney(special.jubileeExempt())
							: "");
			case SurinameCountryRuleKeys.AOV_VACATION_ALLOWANCE, SurinameCountryRuleKeys.AOV_BONUS,
					SurinameCountryRuleKeys.AOV_EXTRA_EARNINGS, SurinameCountryRuleKeys.AOV_OVERTIME ->
				"AOV premium on special payout (4% flat month rule) → " + formatted;
			case SurinameCountryRuleKeys.APF_EMPLOYEE, SurinameCountryRuleKeys.APF_EMPLOYER ->
				PayrollCalculationTraceSupport.appendBreakdown("APF pension share from gross base → " + formatted,
						PayrollCalculationTraceSupport.formatBaseBreakdownFromMap(contributionsByBase,
								SurinameCountryRuleAlgorithms.GROSS_BASE, gross));
			case SurinameCountryRuleKeys.FVO_EMPLOYEE, SurinameCountryRuleKeys.FVO_EMPLOYER ->
				PayrollCalculationTraceSupport.appendBreakdown("FVO premium from label period wage → " + formatted,
						PayrollCalculationTraceSupport.formatLabelPeriodWageBreakdown(loonbelasting, special));
			default -> "Algorithm result → " + formatted;
		};
	}
}
