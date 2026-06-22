package com.wagepayroll.payroll.country;

import java.util.Set;

/**
 * {@code country_rule_key} values on SR wage component templates that are evaluated by
 * {@link SurinameTenantDerivedComponentService} and {@link SurinameCountryRuleAlgorithms}
 * (see FiscLe wage tax summary / {@code platform_country_tax_rule}).
 */
public final class SurinameCountryRuleKeys {

	public static final String TAXABLE_INCOME = "SUR_TAXABLE_INCOME";

	public static final String TAX_FREE_WAGE_TAX = "SUR_TAX_FREE_WAGE_TAX";

	public static final String ACQUISITION_COSTS = "SUR_AQUISITION_COSTS";

	public static final String FREE_MEDICAL_BENEFIT = "SUR_FREE_MEDICAL_BENEFIT";

	public static final String COMPANY_CAR_BENEFIT = "SUR_COMPANY_CAR_BENEFIT";

	public static final String FREE_HOUSING_BENEFIT = "SUR_FREE_HOUSING_BENEFIT";

	public static final String BOARD_LODGING_BENEFIT = "SUR_BOARD_LODGING_BENEFIT";

	public static final String BOARD_BENEFIT = "SUR_BOARD_BENEFIT";

	public static final String HOT_MEAL_BENEFIT = "SUR_HOT_MEAL_BENEFIT";

	public static final String BREAD_MEAL_BENEFIT = "SUR_BREAD_MEAL_BENEFIT";

	/** Free utilities benefit-in-kind (1057); amount from period transaction. */
	public static final String FREE_UTILITIES_BENEFIT = "SUR_FREE_UTILITIES_BENEFIT";

	/** Gross child allowance (1008); factor = number of children from standing instruction quantity. */
	public static final String CHILD_ALLOWANCE = "SUR_CHILD_ALLOWANCE";

	/** Gross exchange-rate compensation cash payout (1055); amount from period transaction. */
	public static final String EXCHANGE_RATE_COMPENSATION = "SUR_EXCHANGE_RATE_COMPENSATION";

	/** Gross pension-scheme payout (1064); amount from period transaction. */
	public static final String PENSION_SCHEME_PAYOUT = "SUR_PENSION_SCHEME_PAYOUT";

	/** Gross cost allowance payout (1058); amount from period transaction. */
	public static final String COST_ALLOWANCE_PAYOUT = "SUR_COST_ALLOWANCE_PAYOUT";

	public static final String RULE_TAX_FREE_WAGE_TAX_YEAR = "SR_TAX_FREE_WAGE_TAX_YEAR";

	/** Per-child monthly rates and caps (tariff type 11). */
	public static final String RULE_CHILD_ALLOWANCE_MONTH = "SR_CHILD_ALLOWANCE_MONTH";

	/** Art. 10 exchange-rate monthly exclusion cap (SRD 800 from 2022-01-01). */
	public static final String RULE_EXCHANGE_RATE_COMPENSATION_MONTH = "SR_EXCHANGE_RATE_COMPENSATION_MONTH";

	/** Monthly AOV beneficiary payout amount (FiscLe); pension exclusion cap = 2×. */
	public static final String RULE_AOV_BENEFICIARY_MONTH = "SR_AOV_BENEFICIARY_MONTH";

	/** Art. 10(k) pension payout monthly exclusion cap (2× {@link #RULE_AOV_BENEFICIARY_MONTH}). */
	public static final String RULE_PENSION_2X_AOV_MONTH = "SR_PENSION_2X_AOV_MONTH";

	public static final String RULE_DEDUCTIBLE_EXPENSES_YEAR = "SR_DEDUCTIBLE_EXPENSES_YEAR";

	public static final String RULE_FREE_MEDICAL_YEAR = "SR_FREE_MEDICAL_YEAR";

	public static final String RULE_COMPANY_CAR_YEAR = "SR_COMPANY_CAR_YEAR";

	public static final String RULE_FREE_HOUSING_YEAR = "SR_FREE_HOUSING_YEAR";

	public static final String RULE_BOARD_LODGING_DAY = "SR_BOARD_LODGING_DAY";

	public static final String RULE_BOARD_DAY = "SR_BOARD_DAY";

	public static final String RULE_HOT_MEAL_UNIT = "SR_HOT_MEAL_UNIT";

	public static final String RULE_BREAD_MEAL_UNIT = "SR_BREAD_MEAL_UNIT";

	public static final String RULE_TAX_FREE_VACATION_YEAR = "SR_TAX_FREE_VACATION_YEAR";

	public static final String RULE_TAX_FREE_BONUS_YEAR = "SR_TAX_FREE_BONUS_YEAR";

	/** Gross vacation allowance (1006); tax on taxable part via art. 17 → 1021. */
	public static final String VACATION_ALLOWANCE = "SUR_VACATION_ALLOWANCE";

	/** Gross bonus (1007); tax on taxable part via art. 17 → 1022. */
	public static final String BONUS = "SUR_BONUS";

	/** Wage tax on vacation — art. 17 label method (1021). */
	public static final String WAGE_TAX_VACATION_ALLOWANCE = "SUR_WAGE_TAX_VACATION_ALLOWANCE";

	/** Wage tax on bonus — art. 17 label method (1022). */
	public static final String WAGE_TAX_BONUS = "SUR_WAGE_TAX_BONUS";

	/** Wage tax on extra earnings — art. 17 label method (1025); fully taxable (no art. 10 cap). */
	public static final String WAGE_TAX_EXTRA_EARNINGS = "SUR_WAGE_TAX_EXTRA_EARNINGS";

	/** AOV premium on extra earnings payout (1018). */
	public static final String AOV_EXTRA_EARNINGS = "SUR_AOV_EXTRA_EARNINGS";

	/** AOV premium on vacation allowance payout (1014). */
	public static final String AOV_VACATION_ALLOWANCE = "SUR_AOV_VACATION_ALLOWANCE";

	/** AOV premium on bonus payout (1015). */
	public static final String AOV_BONUS = "SUR_AOV_BONUS";

	/** AOV premium on overtime payout (1013). */
	public static final String AOV_OVERTIME = "SUR_AOV_OVERTIME";

	/** Wage tax on overtime — {@code SR_OVERTIME_MONTH} brackets (1020). */
	public static final String WAGE_TAX_OVERTIME = "SUR_WAGE_TAX_OVERTIME";

	/** Gross lump-sum payment (1009). */
	public static final String LUMP_SUM = "SUR_LUMP_SUM";

	/** Wage tax on lump sum — {@code SR_PAYMENTS_AT_ONCE_YEAR} brackets (1024). */
	public static final String WAGE_TAX_LUMP_SUM = "SUR_WAGE_TAX_LUMP_SUM";

	public static final String RULE_PAYMENTS_AT_ONCE_YEAR = "SR_PAYMENTS_AT_ONCE_YEAR";

	/** Gross jubilee / service anniversary (1010). */
	public static final String JUBILEE = "SUR_JUBILEE";

	/** Wage tax on jubilee — {@code SR_SERVICE_YEARS_17A_MONTH} (1048). */
	public static final String WAGE_TAX_JUBILEE = "SUR_WAGE_TAX_JUBILEE";

	public static final String RULE_SERVICE_YEARS_17A_MONTH = "SR_SERVICE_YEARS_17A_MONTH";

	/** Employee APF pension share (1044) — half of total premium on clamped gross base. */
	public static final String APF_EMPLOYEE = "SUR_APF";

	/** Employer APF pension share (1043) — same amount as employee share; no net effect. */
	public static final String APF_EMPLOYER = "SUR_APF_EMPLOYER";

	/** Employee FVO premium (1038) — 0.5% of basisloon without emoluments. */
	public static final String FVO_EMPLOYEE = "SUR_FVO_EMPLOYEE";

	/** Employer FVO premium (1037) — 0.5% of basisloon without emoluments. */
	public static final String FVO_EMPLOYER = "SUR_FVO_EMPLOYER";

	/** Net wage informational line (1026) — filled after net pay is computed. */
	public static final String NET_WAGE = "SUR_NET_WAGE";

	public static final String RULE_OVERTIME_MONTH = "SR_OVERTIME_MONTH";

	public static boolean isNetWageDisplayKey(String countryRuleKey) {
		return NET_WAGE.equals(countryRuleKey);
	}

	/** Gross earnings driven by standing quantity or period amount (e.g. 1008, 1055, 1064) — before APF/FVO. */
	public static final Set<String> GROSS_EARNING_DERIVED_KEYS = Set.of(CHILD_ALLOWANCE, EXCHANGE_RATE_COMPENSATION,
			PENSION_SCHEME_PAYOUT, COST_ALLOWANCE_PAYOUT);

	/** APF + FVO — evaluated before tax-adjustment lines (1004–1005). */
	public static final Set<String> PENSION_AND_FVO_DERIVED_KEYS = Set.of(APF_EMPLOYEE, APF_EMPLOYER, FVO_EMPLOYEE,
			FVO_EMPLOYER);

	/** Art. 10(h) exclusion amount on wage-tax child line (1023). */
	public static final String WAGE_TAX_CHILD_ALLOWANCE = "SUR_WAGE_TAX_CHILD_ALLOWANCE";

	/** Art. 10 exchange-rate exclusion amount on wage-tax line (1056). */
	public static final String WAGE_TAX_EXCHANGE_RATE = "SUR_WAGE_TAX_EXCHANGE_RATE";

	/** Art. 10(k) pension payout exclusion amount on wage-tax line (1065). */
	public static final String WAGE_TAX_PENSION_2X_AOV = "SUR_WAGE_TAX_PENSION_2X_AOV";

	/** Art. 10(e) cost allowance exclusion amount on wage-tax line (1059). */
	public static final String WAGE_TAX_COST_ALLOWANCE = "SUR_WAGE_TAX_COST_ALLOWANCE";

	public static final Set<String> TAX_ADJUSTMENT_DERIVED_KEYS = Set.of(TAXABLE_INCOME, TAX_FREE_WAGE_TAX,
			ACQUISITION_COSTS, FREE_MEDICAL_BENEFIT, COMPANY_CAR_BENEFIT, FREE_HOUSING_BENEFIT, BOARD_LODGING_BENEFIT,
			BOARD_BENEFIT, HOT_MEAL_BENEFIT, BREAD_MEAL_BENEFIT, FREE_UTILITIES_BENEFIT, WAGE_TAX_CHILD_ALLOWANCE,
			WAGE_TAX_EXCHANGE_RATE, WAGE_TAX_PENSION_2X_AOV, WAGE_TAX_COST_ALLOWANCE);

	public static final Set<String> SPECIAL_REMUNERATION_DERIVED_KEYS = Set.of(WAGE_TAX_VACATION_ALLOWANCE,
			WAGE_TAX_BONUS, WAGE_TAX_EXTRA_EARNINGS, AOV_VACATION_ALLOWANCE, AOV_BONUS, AOV_EXTRA_EARNINGS,
			AOV_OVERTIME, WAGE_TAX_OVERTIME, WAGE_TAX_LUMP_SUM, WAGE_TAX_JUBILEE);

	public static final Set<String> DERIVED_COMPONENT_KEYS = union(GROSS_EARNING_DERIVED_KEYS,
			PENSION_AND_FVO_DERIVED_KEYS, TAX_ADJUSTMENT_DERIVED_KEYS, SPECIAL_REMUNERATION_DERIVED_KEYS);

	private SurinameCountryRuleKeys() {
	}

	public static boolean isDerivedAlgorithmKey(String countryRuleKey) {
		return countryRuleKey != null && DERIVED_COMPONENT_KEYS.contains(countryRuleKey);
	}

	public static boolean isPensionAndFvoDerivedKey(String countryRuleKey) {
		return countryRuleKey != null && PENSION_AND_FVO_DERIVED_KEYS.contains(countryRuleKey);
	}

	public static boolean isTaxAdjustmentDerivedKey(String countryRuleKey) {
		return countryRuleKey != null && TAX_ADJUSTMENT_DERIVED_KEYS.contains(countryRuleKey);
	}

	public static boolean isSpecialRemunerationDerivedKey(String countryRuleKey) {
		return countryRuleKey != null && SPECIAL_REMUNERATION_DERIVED_KEYS.contains(countryRuleKey);
	}

	@SafeVarargs
	private static Set<String> union(Set<String>... sets) {
		java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
		for (Set<String> set : sets) {
			out.addAll(set);
		}
		return Set.copyOf(out);
	}

}
