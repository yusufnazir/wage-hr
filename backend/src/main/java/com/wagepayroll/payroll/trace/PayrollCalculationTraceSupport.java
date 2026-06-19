package com.wagepayroll.payroll.trace;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.wagepayroll.domain.wagecomponent.TenantWageComponentEntity;
import com.wagepayroll.payroll.base.PayrollBaseContribution;
import com.wagepayroll.payroll.country.SurinameCountryRuleAlgorithms;
import com.wagepayroll.payroll.country.SurinameJubileeSupport;
import com.wagepayroll.payroll.country.SurinameSpecialRemunerationSupport;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentTransactionEntity;
import com.wagepayroll.payroll.model.CalculationMethod;
import com.wagepayroll.payroll.model.NetEffect;

public final class PayrollCalculationTraceSupport {

	private static final RoundingMode ROUND = RoundingMode.HALF_UP;

	private PayrollCalculationTraceSupport() {
	}

	public static String payEffectLabel(NetEffect netEffect) {
		if (netEffect == null) {
			return "—";
		}
		return switch (netEffect) {
			case ADD_TO_NET -> "Earning (adds to net pay)";
			case SUBTRACT_FROM_NET -> "Deduction (subtracts from net pay)";
			case NO_EFFECT -> "Informational (no net pay effect)";
		};
	}

	public static String taxationSummary(TenantWageComponentEntity comp) {
		List<String> flags = new ArrayList<>();
		if (comp.isTaxableWageTax()) {
			flags.add("wage tax");
		}
		if (comp.isTaxableSocialSecurity()) {
			flags.add("social security (AOV)");
		}
		if (comp.isTaxablePension()) {
			flags.add("pension");
		}
		if (comp.isTaxableVacationReserve()) {
			flags.add("vacation reserve");
		}
		if (flags.isEmpty()) {
			return "Not taxable";
		}
		return "Taxable: " + String.join(", ", flags);
	}

	public static String formatMoney(BigDecimal value) {
		if (value == null) {
			return "—";
		}
		return value.setScale(4, ROUND).stripTrailingZeros().toPlainString();
	}

	public static String formatBaseBreakdown(String baseCode, List<PayrollBaseContribution> contributions,
			BigDecimal total) {
		if (contributions == null || contributions.isEmpty()) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		sb.append(baseCode).append(" from wage components:");
		for (PayrollBaseContribution contribution : contributions) {
			sb.append('\n').append("  ");
			sb.append(contribution.baseDelta().signum() >= 0 ? "+" : "−");
			sb.append(" [").append(contribution.componentCode()).append("] ");
			sb.append(formatMoney(contribution.baseDelta().abs()));
		}
		if (total != null) {
			sb.append('\n').append("Total ").append(baseCode).append(": ").append(formatMoney(total));
		}
		return sb.toString();
	}

	public static String formatBaseBreakdownFromMap(Map<String, List<PayrollBaseContribution>> contributionsByBase,
			String baseCode, BigDecimal total) {
		if (contributionsByBase == null || contributionsByBase.isEmpty()) {
			return "";
		}
		return formatBaseBreakdown(baseCode, contributionsByBase.get(baseCode), total);
	}

	public static String formatFormulaComponentDependencies(Collection<String> componentCodes,
			Map<String, BigDecimal> amountsByCode) {
		if (componentCodes == null || componentCodes.isEmpty() || amountsByCode == null || amountsByCode.isEmpty()) {
			return "";
		}
		StringBuilder sb = new StringBuilder("Uses amounts from other components:");
		for (String code : componentCodes) {
			sb.append('\n').append("  [").append(code).append("] ");
			sb.append(formatMoney(amountsByCode.get(code)));
		}
		return sb.toString();
	}

	public static String formatLabelPeriodWageBreakdown(BigDecimal loonbelasting,
			SurinameSpecialRemunerationSupport.Amounts special) {
		if (special == null) {
			return "";
		}
		StringBuilder sb = new StringBuilder("Label period wage = LOONBELASTING ");
		sb.append(formatMoney(loonbelasting));
		appendSpecialPayoutLine(sb, SurinameSpecialRemunerationSupport.VACATION_COMPONENT_CODE, "vacation payout",
				special.vacationPayout());
		appendSpecialPayoutLine(sb, SurinameSpecialRemunerationSupport.BONUS_COMPONENT_CODE, "bonus payout",
				special.bonusPayout());
		if (special.overtimePayout() != null && special.overtimePayout().signum() > 0) {
			sb.append('\n').append("  − [1045–1047] overtime payout ");
			sb.append(formatMoney(special.overtimePayout()));
		}
		appendSpecialPayoutLine(sb, SurinameSpecialRemunerationSupport.LUMP_SUM_COMPONENT_CODE, "lump sum payout",
				special.lumpSumPayout());
		appendSpecialPayoutLine(sb, SurinameJubileeSupport.JUBILEE_COMPONENT_CODE, "jubilee payout",
				special.jubileePayout());
		appendSpecialPayoutLine(sb, SurinameSpecialRemunerationSupport.EXTRA_EARNINGS_COMPONENT_CODE,
				"extra earnings payout", special.extraEarningsPayout());
		sb.append('\n').append("= ").append(formatMoney(special.labelPeriodWage()));
		return sb.toString();
	}

	public static String formatSpecialPayoutSource(String componentCode, String label, BigDecimal payout,
			BigDecimal exempt, BigDecimal taxable) {
		if (payout == null || payout.signum() <= 0) {
			return "";
		}
		StringBuilder sb = new StringBuilder("Source: [").append(componentCode).append("] ").append(label).append(" ");
		sb.append(formatMoney(payout));
		if (exempt != null && exempt.signum() > 0) {
			sb.append(", exempt ").append(formatMoney(exempt));
		}
		if (taxable != null) {
			sb.append(", taxable ").append(formatMoney(taxable));
		}
		return sb.toString();
	}

	private static void appendSpecialPayoutLine(StringBuilder sb, String componentCode, String label,
			BigDecimal payout) {
		if (payout != null && payout.signum() > 0) {
			sb.append('\n').append("  − [").append(componentCode).append("] ").append(label).append(' ');
			sb.append(formatMoney(payout));
		}
	}

	public static String appendBreakdown(String headline, String breakdown) {
		if (breakdown == null || breakdown.isBlank()) {
			return headline;
		}
		return headline + "\n" + breakdown;
	}

	public static String loonbelastingBaseCode() {
		return SurinameCountryRuleAlgorithms.LOONBELASTING_BASE;
	}

	public static String grossBaseCode() {
		return SurinameCountryRuleAlgorithms.GROSS_BASE;
	}

	public static String formatFactor(BigDecimal quantity, BigDecimal rate) {
		if (quantity != null && rate != null && quantity.signum() != 0 && rate.signum() != 0) {
			return formatMoney(quantity) + " × " + formatMoney(rate);
		}
		if (quantity != null && quantity.signum() != 0) {
			return formatMoney(quantity);
		}
		if (rate != null && rate.signum() != 0) {
			return formatMoney(rate);
		}
		return "—";
	}

	public static boolean isManualTransactionAmountOverride(TenantWageComponentTransactionEntity tx,
			BigDecimal txnAmount) {
		return tx != null && tx.isManualOverride() && txnAmount != null && txnAmount.signum() != 0;
	}

	/**
	 * Standing instruction {@code amountOverride} materializes a non-zero period amount with no factor (qty 0).
	 */
	public static boolean isStandingInstructionAmountOverride(TenantWageComponentTransactionEntity tx,
			BigDecimal quantity, BigDecimal txnAmount) {
		if (tx == null || tx.isManualOverride() || txnAmount == null || txnAmount.signum() == 0) {
			return false;
		}
		return quantity == null || quantity.signum() == 0;
	}

	public static String tenantFactorExplanation(CalculationMethod method, BigDecimal quantity, BigDecimal rate,
			BigDecimal txnAmount, BigDecimal defaultAmount, BigDecimal periodicRate, BigDecimal hourlyRate,
			String resolvedFormula) {
		return tenantFactorExplanation(method, quantity, rate, txnAmount, defaultAmount, periodicRate, hourlyRate,
				resolvedFormula, false, false);
	}

	public static String tenantFactorExplanation(CalculationMethod method, BigDecimal quantity, BigDecimal rate,
			BigDecimal txnAmount, BigDecimal defaultAmount, BigDecimal periodicRate, BigDecimal hourlyRate,
			String resolvedFormula, boolean manualTransactionOverride, boolean standingInstructionAmountOverride) {
		return switch (method) {
			case HOURLY -> "Amount = quantity × rate from period transaction (standing instruction materialized to "
					+ formatFactor(quantity, rate) + ").";
			case FIXED_AMOUNT -> txnAmount != null && txnAmount.signum() != 0
					? "Amount = period transaction amount " + formatMoney(txnAmount)
							+ " (from standing instruction or manual entry)."
					: "Amount = component default " + formatMoney(defaultAmount) + " (no period transaction amount).";
			case MANUAL_INPUT -> "Amount = manual period transaction amount " + formatMoney(txnAmount) + ".";
			case FORMULA -> {
				if (manualTransactionOverride) {
					yield "Manual period transaction override: amount " + formatMoney(txnAmount)
							+ " replaces formula evaluation"
							+ (resolvedFormula != null && !resolvedFormula.isBlank() ? " (" + resolvedFormula + ")." : ".");
				}
				if (standingInstructionAmountOverride) {
					yield "Standing instruction amount override: period transaction amount "
							+ formatMoney(txnAmount) + " replaces formula evaluation"
							+ (resolvedFormula != null && !resolvedFormula.isBlank() ? " (" + resolvedFormula + ")." : ".");
				}
				StringBuilder sb = new StringBuilder("Formula evaluated");
				if (resolvedFormula != null && !resolvedFormula.isBlank()) {
					sb.append(": ").append(resolvedFormula);
				}
				sb.append(". Inputs: periodic rate ").append(formatMoney(periodicRate));
				sb.append(", hourly rate ").append(formatMoney(hourlyRate));
				sb.append(", transaction qty ").append(formatMoney(quantity));
				sb.append(", transaction rate ").append(formatMoney(rate));
				sb.append(", transaction amount ").append(formatMoney(txnAmount));
				sb.append(", default ").append(formatMoney(defaultAmount)).append(".");
				yield sb.toString();
			}
			case PERCENTAGE -> "Percentage method — evaluated in a later pass.";
		};
	}

	public static String tenantAmountExplanation(CalculationMethod method, BigDecimal quantity, BigDecimal rate,
			BigDecimal txnAmount, BigDecimal defaultAmount, BigDecimal amount) {
		return tenantAmountExplanation(method, quantity, rate, txnAmount, defaultAmount, amount, null, false, false);
	}

	public static String tenantAmountExplanation(CalculationMethod method, BigDecimal quantity, BigDecimal rate,
			BigDecimal txnAmount, BigDecimal defaultAmount, BigDecimal amount, BigDecimal formulaResultWithoutOverride,
			boolean manualTransactionOverride, boolean standingInstructionAmountOverride) {
		return switch (method) {
			case HOURLY -> formatFactor(quantity, rate) + " = " + formatMoney(amount);
			case FIXED_AMOUNT -> (txnAmount != null && txnAmount.signum() != 0 ? "Transaction" : "Default") + " amount → "
					+ formatMoney(amount);
			case MANUAL_INPUT -> "Manual input → " + formatMoney(amount);
			case FORMULA -> {
				if (manualTransactionOverride) {
					yield "Manual override → " + formatMoney(amount)
							+ (formulaResultWithoutOverride != null
									? " (formula would yield " + formatMoney(formulaResultWithoutOverride) + ")"
									: "");
				}
				if (standingInstructionAmountOverride) {
					yield "Standing instruction amount override → " + formatMoney(amount)
							+ (formulaResultWithoutOverride != null
									? " (formula would yield " + formatMoney(formulaResultWithoutOverride) + ")"
									: "");
				}
				yield "Formula result → " + formatMoney(amount);
			}
			case PERCENTAGE -> "—";
		};
	}
}
