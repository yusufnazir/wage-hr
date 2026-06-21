package com.wagepayroll.payroll.country;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.wagepayroll.domain.compensation.TenantEmployeeCompensationEntity;
import com.wagepayroll.domain.compensation.TenantEmployeeCompensationRepository;
import com.wagepayroll.domain.org.TenantCompanyEntity;
import com.wagepayroll.domain.org.TenantCompanyRepository;
import com.wagepayroll.domain.org.TenantEmployeeEntity;
import com.wagepayroll.domain.org.TenantEmployeeRepository;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentEntity;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentTransactionEntity;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentTransactionRepository;
import com.wagepayroll.payroll.base.PayrollBaseAccumulationResult;
import com.wagepayroll.payroll.base.PayrollBaseAccumulator;
import com.wagepayroll.payroll.formula.CompensationFormulaSupport;
import com.wagepayroll.payroll.engine.CountryRuleContext;
import com.wagepayroll.payroll.engine.EvaluatedComponentAmount;
import com.wagepayroll.payroll.engine.PayrollContext;
import com.wagepayroll.payroll.engine.PayrollRunState;
import com.wagepayroll.payroll.trace.PayrollCalculationTraceDerivedExplanations;

/**
 * Evaluates tenant wage components whose {@code country_rule_key} maps to Suriname payroll algorithms.
 */
@Service
public class SurinameTenantDerivedComponentService {

	private static final String SR = "SR";

	private final SurinameCountryRuleAlgorithms algorithms;

	private final SurinameWageTaxCalculator wageTaxCalculator;

	private final SurinameApfCalculator apfCalculator;

	private final SurinameFvoCalculator fvoCalculator;

	private final PayrollBaseAccumulator payrollBaseAccumulator;

	private final TenantEmployeeCompensationRepository compensationRepository;

	private final TenantCompanyRepository companyRepository;

	private final TenantEmployeeRepository employeeRepository;

	private final TenantWageComponentTransactionRepository transactionRepository;

	public SurinameTenantDerivedComponentService(SurinameCountryRuleAlgorithms algorithms,
			SurinameWageTaxCalculator wageTaxCalculator, SurinameApfCalculator apfCalculator,
			SurinameFvoCalculator fvoCalculator, PayrollBaseAccumulator payrollBaseAccumulator,
			TenantEmployeeCompensationRepository compensationRepository, TenantCompanyRepository companyRepository,
			TenantEmployeeRepository employeeRepository,
			TenantWageComponentTransactionRepository transactionRepository) {
		this.algorithms = algorithms;
		this.wageTaxCalculator = wageTaxCalculator;
		this.apfCalculator = apfCalculator;
		this.fvoCalculator = fvoCalculator;
		this.payrollBaseAccumulator = payrollBaseAccumulator;
		this.compensationRepository = compensationRepository;
		this.companyRepository = companyRepository;
		this.employeeRepository = employeeRepository;
		this.transactionRepository = transactionRepository;
	}

	public boolean supports(String payrollCountryIso2) {
		return SR.equalsIgnoreCase(payrollCountryIso2);
	}

	/**
	 * Adds or replaces evaluated lines for algorithm-driven components in tiers:
	 * <ol>
	 * <li>Gross earnings from standing factor (e.g. child allowance × children)</li>
	 * <li>APF + FVO on gross/label wage</li>
	 * <li>Re-accumulate bases (e.g. APF employee → LOONBELASTING)</li>
	 * <li>Tax adjustment lines (1004 taxable amount, 1005, …)</li>
	 * <li>Art. 17 / overtime AOV and wage tax lines</li>
	 * </ol>
	 */
	public List<EvaluatedComponentAmount> applyDerivedLines(PayrollContext context, CountryRuleContext countryContext,
			List<TenantWageComponentEntity> tenantComponents, List<EvaluatedComponentAmount> evaluated,
			Map<UUID, Map<String, BigDecimal>> baseTotals, PayrollRunState state) {
		List<TenantWageComponentEntity> derivedComponents = tenantComponents.stream()
				.filter(c -> SurinameCountryRuleKeys.isDerivedAlgorithmKey(c.getCountryRuleKey())).toList();
		if (derivedComponents.isEmpty()) {
			return evaluated;
		}
		SurinameTaxRulesSnapshot snapshot = countryContext
				.findAttribute(SurinameCountryContextKeys.TAX_RULES_SNAPSHOT, SurinameTaxRulesSnapshot.class)
				.orElse(null);
		int periods = SurinameWageTaxCalculator.DEFAULT_PERIODS_PER_YEAR;
		Map<UUID, TenantEmployeeCompensationEntity> compensationByEmployee = loadCompensation(context);
		ResolvedSurinameTaxRule wageTaxRule = snapshot != null
				? snapshot.rulesByCode().get("SR_WAGE_TAX_DEFAULT")
				: null;
		ResolvedSurinameTaxRule aovRule = snapshot != null ? snapshot.rulesByCode().get("SR_AOV_PREMIUM_MONTH") : null;
		ResolvedSurinameTaxRule overtimeTaxRule = snapshot != null
				? snapshot.rulesByCode().get(SurinameCountryRuleKeys.RULE_OVERTIME_MONTH)
				: null;
		ResolvedSurinameTaxRule paymentsAtOnceTaxRule = snapshot != null
				? snapshot.rulesByCode().get(SurinameCountryRuleKeys.RULE_PAYMENTS_AT_ONCE_YEAR)
				: null;
		ResolvedSurinameTaxRule jubileeTaxRule = snapshot != null
				? snapshot.rulesByCode().get(SurinameCountryRuleKeys.RULE_SERVICE_YEARS_17A_MONTH)
				: null;
		ResolvedSurinameTaxRule apfRule = snapshot != null
				? snapshot.rulesByCode().get(SurinameApfCalculator.RULE_CODE)
				: null;
		ResolvedSurinameTaxRule fvoRule = snapshot != null
				? snapshot.rulesByCode().get(SurinameFvoCalculator.RULE_CODE)
				: null;
		int calendarYear = calendarYear(context);
		LocalDate serviceYearsAsOf = context.countryRulesAsOf() != null ? context.countryRulesAsOf()
				: LocalDate.now(ZoneOffset.UTC);
		Map<UUID, BigDecimal> referenceMonthWageByEmployee = referenceMonthWages(context, compensationByEmployee);
		Map<UUID, Integer> serviceYearsByEmployee = serviceYearsByEmployee(context, serviceYearsAsOf);
		Map<String, EvaluatedComponentAmount> byKey = indexByLineKey(evaluated);
		Map<UUID, Map<String, BigDecimal>> workingBases = baseTotals;
		Map<UUID, Map<UUID, BigDecimal>> quantityByEmployeeAndComponent = loadPeriodQuantities(context);
		Map<UUID, Map<UUID, BigDecimal>> amountByEmployeeAndComponent = loadPeriodAmounts(context);
		UUID childAllowanceComponentId = derivedComponents.stream()
				.filter(c -> SurinameCountryRuleKeys.CHILD_ALLOWANCE.equals(c.getCountryRuleKey()))
				.map(TenantWageComponentEntity::getId)
				.findFirst()
				.orElse(null);
		UUID exchangeRateCompensationComponentId = derivedComponents.stream()
				.filter(c -> SurinameCountryRuleKeys.EXCHANGE_RATE_COMPENSATION.equals(c.getCountryRuleKey()))
				.map(TenantWageComponentEntity::getId)
				.findFirst()
				.orElse(null);

		applyTier(context, derivedComponents, byKey, workingBases, compensationByEmployee, referenceMonthWageByEmployee,
				snapshot, periods, calendarYear, wageTaxRule, aovRule, overtimeTaxRule, paymentsAtOnceTaxRule,
				jubileeTaxRule, apfRule, fvoRule, SurinameCountryRuleKeys.GROSS_EARNING_DERIVED_KEYS, null,
				quantityByEmployeeAndComponent, amountByEmployeeAndComponent, childAllowanceComponentId,
				exchangeRateCompensationComponentId, serviceYearsByEmployee, state);

		workingBases = reaccumulateBases(context, byKey);

		applyTier(context, derivedComponents, byKey, workingBases, compensationByEmployee, referenceMonthWageByEmployee,
				snapshot, periods, calendarYear, wageTaxRule, aovRule, overtimeTaxRule, paymentsAtOnceTaxRule,
				jubileeTaxRule, apfRule, fvoRule, SurinameCountryRuleKeys.PENSION_AND_FVO_DERIVED_KEYS, null,
				quantityByEmployeeAndComponent, amountByEmployeeAndComponent, childAllowanceComponentId,
				exchangeRateCompensationComponentId, serviceYearsByEmployee, state);

		workingBases = reaccumulateBases(context, byKey);
		Map<UUID, SurinameSpecialRemunerationSupport.Amounts> specialByEmployee = SurinameSpecialRemunerationSupport
				.amountsByEmployee(context.employeeIds(), List.copyOf(byKey.values()), workingBases,
						referenceMonthWageByEmployee, serviceYearsByEmployee, snapshot, periods);

		applyTier(context, derivedComponents, byKey, workingBases, compensationByEmployee, referenceMonthWageByEmployee,
				snapshot, periods, calendarYear, wageTaxRule, aovRule, overtimeTaxRule, paymentsAtOnceTaxRule,
				jubileeTaxRule, apfRule, fvoRule, SurinameCountryRuleKeys.TAX_ADJUSTMENT_DERIVED_KEYS, specialByEmployee,
				quantityByEmployeeAndComponent, amountByEmployeeAndComponent, childAllowanceComponentId,
				exchangeRateCompensationComponentId, serviceYearsByEmployee, state);

		applyTier(context, derivedComponents, byKey, workingBases, compensationByEmployee, referenceMonthWageByEmployee,
				snapshot, periods, calendarYear, wageTaxRule, aovRule, overtimeTaxRule, paymentsAtOnceTaxRule,
				jubileeTaxRule, apfRule, fvoRule, SurinameCountryRuleKeys.SPECIAL_REMUNERATION_DERIVED_KEYS,
				specialByEmployee, quantityByEmployeeAndComponent, amountByEmployeeAndComponent, childAllowanceComponentId,
				exchangeRateCompensationComponentId, serviceYearsByEmployee, state);

		return List.copyOf(byKey.values());
	}

	private void applyTier(PayrollContext context, List<TenantWageComponentEntity> derivedComponents,
			Map<String, EvaluatedComponentAmount> byKey, Map<UUID, Map<String, BigDecimal>> baseTotals,
			Map<UUID, TenantEmployeeCompensationEntity> compensationByEmployee,
			Map<UUID, BigDecimal> referenceMonthWageByEmployee, SurinameTaxRulesSnapshot snapshot, int periods,
			int calendarYear, ResolvedSurinameTaxRule wageTaxRule, ResolvedSurinameTaxRule aovRule,
			ResolvedSurinameTaxRule overtimeTaxRule, ResolvedSurinameTaxRule paymentsAtOnceTaxRule,
			ResolvedSurinameTaxRule jubileeTaxRule, ResolvedSurinameTaxRule apfRule, ResolvedSurinameTaxRule fvoRule,
			Set<String> tierKeys, Map<UUID, SurinameSpecialRemunerationSupport.Amounts> specialByEmployee,
			Map<UUID, Map<UUID, BigDecimal>> quantityByEmployeeAndComponent,
			Map<UUID, Map<UUID, BigDecimal>> amountByEmployeeAndComponent, UUID childAllowanceComponentId,
			UUID exchangeRateCompensationComponentId, Map<UUID, Integer> serviceYearsByEmployee, PayrollRunState state) {
		List<TenantWageComponentEntity> tierComponents = derivedComponents.stream()
				.filter(c -> tierKeys.contains(c.getCountryRuleKey())).toList();
		if (tierComponents.isEmpty()) {
			return;
		}
		PayrollBaseAccumulationResult baseDetail = payrollBaseAccumulator.accumulateDetailed(context.tenantId(),
				List.copyOf(byKey.values()));
		Map<UUID, Map<String, BigDecimal>> tierBases = baseDetail.totalsByEmployee();
		for (UUID employeeId : context.employeeIds()) {
			Map<String, BigDecimal> bases = tierBases.getOrDefault(employeeId, Map.of());
			var contributionsByBase = baseDetail.contributionsByEmployee().getOrDefault(employeeId, Map.of());
			BigDecimal loonbelasting = bases.getOrDefault(SurinameCountryRuleAlgorithms.LOONBELASTING_BASE,
					BigDecimal.ZERO);
			BigDecimal gross = bases.getOrDefault(SurinameCountryRuleAlgorithms.GROSS_BASE, BigDecimal.ZERO);
			TenantEmployeeCompensationEntity compensation = compensationByEmployee.get(employeeId);
			boolean applyTaxExempt = compensation == null || compensation.isApplyTaxExempt();
			boolean applyAov = compensation == null || compensation.isApplyAov();
			boolean applyTaxes = compensation == null || compensation.isApplyTaxes();
			SurinameSpecialRemunerationSupport.Amounts special = specialByEmployee != null
					? specialByEmployee.get(employeeId)
					: null;
			if (special == null) {
				special = SurinameSpecialRemunerationSupport.amountsByEmployee(List.of(employeeId),
						List.copyOf(byKey.values()), tierBases, referenceMonthWageByEmployee, serviceYearsByEmployee,
						snapshot, periods).get(employeeId);
			}
			Map<UUID, BigDecimal> quantitiesForEmployee = quantityByEmployeeAndComponent.getOrDefault(employeeId,
					Map.of());
			Map<UUID, BigDecimal> amountsForEmployee = amountByEmployeeAndComponent.getOrDefault(employeeId, Map.of());
			for (TenantWageComponentEntity comp : tierComponents) {
				BigDecimal childrenCount = payrollInputQuantity(comp, quantitiesForEmployee, childAllowanceComponentId);
				BigDecimal listPrice = resolveListPrice(comp, amountsForEmployee);
				BigDecimal exchangeRatePayout = resolveExchangeRatePayout(amountsForEmployee,
						exchangeRateCompensationComponentId);
				BigDecimal amount = resolveAmount(comp.getCountryRuleKey(), loonbelasting, gross, snapshot,
						applyTaxExempt, applyTaxes, applyAov, periods, special, wageTaxRule, aovRule,
						overtimeTaxRule, paymentsAtOnceTaxRule, jubileeTaxRule, apfRule, fvoRule, calendarYear,
						childrenCount, referenceMonthWageByEmployee.get(employeeId), listPrice, exchangeRatePayout);
				if (state != null) {
					state.calculationTrace().addTenantComponent("DERIVED", employeeId, comp, childrenCount, listPrice,
							PayrollCalculationTraceDerivedExplanations.factorForCountryRule(comp.getCountryRuleKey(),
									childrenCount, listPrice, exchangeRatePayout),
							amount,
							PayrollCalculationTraceDerivedExplanations.amountForCountryRule(comp.getCountryRuleKey(),
									loonbelasting, gross, special, childrenCount, amount, contributionsByBase),
							comp.getFormulaExpression(), true, null);
				}
				byKey.put(lineKey(employeeId, comp.getId()),
						EvaluatedComponentAmount.tenant(employeeId, comp.getId(), comp.getCode(),
								comp.getCalculationMethod().name(), amount, comp.getFormulaExpression()));
			}
		}
	}

	private Map<UUID, Map<String, BigDecimal>> reaccumulateBases(PayrollContext context,
			Map<String, EvaluatedComponentAmount> byKey) {
		return payrollBaseAccumulator.accumulateForEmployees(context.tenantId(), List.copyOf(byKey.values()));
	}

	private static Map<String, EvaluatedComponentAmount> indexByLineKey(List<EvaluatedComponentAmount> evaluated) {
		Map<String, EvaluatedComponentAmount> byKey = new HashMap<>();
		for (EvaluatedComponentAmount line : evaluated) {
			byKey.put(lineKey(line.employeeId(), line.tenantWageComponentId()), line);
		}
		return byKey;
	}

	private Map<UUID, BigDecimal> referenceMonthWages(PayrollContext context,
			Map<UUID, TenantEmployeeCompensationEntity> compensationByEmployee) {
		TenantCompanyEntity company = companyRepository.findByIdAndTenantId(context.companyId(), context.tenantId())
				.orElse(null);
		Map<UUID, BigDecimal> out = new HashMap<>();
		for (UUID employeeId : context.employeeIds()) {
			TenantEmployeeCompensationEntity compensation = compensationByEmployee.get(employeeId);
			if (compensation != null && compensation.getWageAmount() != null && company != null) {
				out.put(employeeId, CompensationFormulaSupport.periodAmount(compensation, company));
			}
		}
		return out;
	}

	private Map<UUID, Map<UUID, BigDecimal>> loadPeriodQuantities(PayrollContext context) {
		if (context.payPeriodId() == null || context.employeeIds().isEmpty()) {
			return Map.of();
		}
		List<TenantWageComponentTransactionEntity> rows = context.employeeIds().size() <= 50
				? transactionRepository.findByTenantIdAndCompanyIdAndPayPeriodIdAndEmployeeIdIn(context.tenantId(),
						context.companyId(), context.payPeriodId(), context.employeeIds())
				: transactionRepository.findByTenantIdAndCompanyIdAndPayPeriodId(context.tenantId(),
						context.companyId(), context.payPeriodId());
		Map<UUID, Map<UUID, BigDecimal>> out = new HashMap<>();
		for (TenantWageComponentTransactionEntity row : rows) {
			if (!context.employeeIds().contains(row.getEmployeeId()) || row.getQuantity() == null
					|| row.getQuantity().signum() <= 0) {
				continue;
			}
			out.computeIfAbsent(row.getEmployeeId(), ignored -> new HashMap<>()).put(row.getTenantWageComponentId(),
					row.getQuantity());
		}
		return out;
	}

	private Map<UUID, Map<UUID, BigDecimal>> loadPeriodAmounts(PayrollContext context) {
		if (context.payPeriodId() == null || context.employeeIds().isEmpty()) {
			return Map.of();
		}
		List<TenantWageComponentTransactionEntity> rows = context.employeeIds().size() <= 50
				? transactionRepository.findByTenantIdAndCompanyIdAndPayPeriodIdAndEmployeeIdIn(context.tenantId(),
						context.companyId(), context.payPeriodId(), context.employeeIds())
				: transactionRepository.findByTenantIdAndCompanyIdAndPayPeriodId(context.tenantId(),
						context.companyId(), context.payPeriodId());
		Map<UUID, Map<UUID, BigDecimal>> out = new HashMap<>();
		for (TenantWageComponentTransactionEntity row : rows) {
			if (!context.employeeIds().contains(row.getEmployeeId()) || row.getAmount() == null
					|| row.getAmount().signum() <= 0) {
				continue;
			}
			out.computeIfAbsent(row.getEmployeeId(), ignored -> new HashMap<>()).put(row.getTenantWageComponentId(),
					row.getAmount());
		}
		return out;
	}

	private BigDecimal resolveAmount(String countryRuleKey, BigDecimal loonbelasting, BigDecimal gross,
			SurinameTaxRulesSnapshot snapshot, boolean applyTaxExempt, boolean applyTaxes, boolean applyAov,
			int periods, SurinameSpecialRemunerationSupport.Amounts special, ResolvedSurinameTaxRule wageTaxRule,
			ResolvedSurinameTaxRule aovRule, ResolvedSurinameTaxRule overtimeTaxRule,
			ResolvedSurinameTaxRule paymentsAtOnceTaxRule, ResolvedSurinameTaxRule jubileeTaxRule,
			ResolvedSurinameTaxRule apfRule, ResolvedSurinameTaxRule fvoRule, int calendarYear,
			BigDecimal payrollInputQuantity, BigDecimal referenceMonthWage, BigDecimal listPrice,
			BigDecimal exchangeRatePayout) {
		return switch (countryRuleKey) {
			case SurinameCountryRuleKeys.CHILD_ALLOWANCE ->
				algorithms.periodChildAllowanceGrossAmount(snapshot, payrollInputQuantity);
			case SurinameCountryRuleKeys.EXCHANGE_RATE_COMPENSATION ->
				algorithms.periodExchangeRateCompensationPayout(exchangeRatePayout);
			case SurinameCountryRuleKeys.WAGE_TAX_CHILD_ALLOWANCE ->
				algorithms.periodChildAllowanceExcludedFromLoon(snapshot, payrollInputQuantity);
			case SurinameCountryRuleKeys.WAGE_TAX_EXCHANGE_RATE ->
				algorithms.periodExchangeRateCompensationExcludedFromLoon(snapshot, exchangeRatePayout);
			case SurinameCountryRuleKeys.TAXABLE_INCOME -> algorithms.taxableIncomeAmount(loonbelasting);
			case SurinameCountryRuleKeys.TAX_FREE_WAGE_TAX -> algorithms.periodTaxExemptApplied(snapshot, applyTaxExempt,
					periods, loonbelasting);
			case SurinameCountryRuleKeys.ACQUISITION_COSTS -> algorithms.periodDeductibleCosts(gross, snapshot, periods);
			case SurinameCountryRuleKeys.FREE_MEDICAL_BENEFIT ->
				algorithms.periodFreeMedicalBenefit(algorithms.moneyWageBasePreBenefitInKind(loonbelasting), periods);
			case SurinameCountryRuleKeys.COMPANY_CAR_BENEFIT ->
				algorithms.periodCompanyCarBenefit(listPrice, snapshot, periods);
			case SurinameCountryRuleKeys.FREE_HOUSING_BENEFIT ->
				algorithms.periodFreeHousingBenefit(loonbelasting, snapshot, periods);
			case SurinameCountryRuleKeys.BOARD_LODGING_BENEFIT ->
				algorithms.periodBoardLodgingBenefit(payrollInputQuantity, snapshot);
			case SurinameCountryRuleKeys.BOARD_BENEFIT -> algorithms.periodBoardBenefit(payrollInputQuantity, snapshot);
			case SurinameCountryRuleKeys.HOT_MEAL_BENEFIT -> algorithms.periodHotMealBenefit(payrollInputQuantity, snapshot);
			case SurinameCountryRuleKeys.BREAD_MEAL_BENEFIT ->
				algorithms.periodBreadMealBenefit(payrollInputQuantity, snapshot);
			case SurinameCountryRuleKeys.FREE_UTILITIES_BENEFIT ->
				algorithms.periodFreeUtilitiesBenefit(listPrice);
			case SurinameCountryRuleKeys.WAGE_TAX_VACATION_ALLOWANCE -> art17WageTax(special.vacationTaxable(),
					special.labelPeriodWage(), wageTaxRule, periods);
			case SurinameCountryRuleKeys.WAGE_TAX_BONUS -> art17WageTax(special.bonusTaxable(), special.labelPeriodWage(),
					wageTaxRule, periods);
			case SurinameCountryRuleKeys.WAGE_TAX_EXTRA_EARNINGS -> art17WageTax(special.extraEarningsTaxable(),
					special.labelPeriodWage(), wageTaxRule, periods);
			case SurinameCountryRuleKeys.AOV_VACATION_ALLOWANCE ->
				aovPremium(applyAov, special.vacationPayout(), aovRule, periods);
			case SurinameCountryRuleKeys.AOV_BONUS -> aovPremium(applyAov, special.bonusPayout(), aovRule, periods);
			case SurinameCountryRuleKeys.AOV_EXTRA_EARNINGS ->
				aovPremium(applyAov, special.extraEarningsPayout(), aovRule, periods);
			case SurinameCountryRuleKeys.AOV_OVERTIME -> aovPremium(applyAov, special.overtimePayout(), aovRule, periods);
			case SurinameCountryRuleKeys.WAGE_TAX_OVERTIME ->
				overtimeWageTax(applyTaxes, special.overtimePayout(), overtimeTaxRule, periods);
			case SurinameCountryRuleKeys.WAGE_TAX_LUMP_SUM ->
				lumpSumWageTax(applyTaxes, special.lumpSumPayout(), paymentsAtOnceTaxRule);
			case SurinameCountryRuleKeys.WAGE_TAX_JUBILEE -> jubileeWageTax(applyTaxes, special, paymentsAtOnceTaxRule);
			case SurinameCountryRuleKeys.APF_EMPLOYEE, SurinameCountryRuleKeys.APF_EMPLOYER ->
				apfCalculator.computePartyShare(gross, calendarYear, apfRule);
			case SurinameCountryRuleKeys.FVO_EMPLOYEE, SurinameCountryRuleKeys.FVO_EMPLOYER ->
				fvoCalculator.computePartyShare(special.labelPeriodWage(), fvoRule);
			default -> BigDecimal.ZERO.setScale(4, java.math.RoundingMode.HALF_UP);
		};
	}

	private static BigDecimal resolveListPrice(TenantWageComponentEntity comp, Map<UUID, BigDecimal> amountsForEmployee) {
		BigDecimal txnAmount = amountsForEmployee.get(comp.getId());
		if (txnAmount != null && txnAmount.signum() > 0) {
			return txnAmount;
		}
		return comp.getDefaultAmount();
	}

	private static BigDecimal resolveExchangeRatePayout(Map<UUID, BigDecimal> amountsForEmployee,
			UUID exchangeRateCompensationComponentId) {
		if (exchangeRateCompensationComponentId == null) {
			return null;
		}
		return amountsForEmployee.get(exchangeRateCompensationComponentId);
	}

	private static BigDecimal payrollInputQuantity(TenantWageComponentEntity comp,
			Map<UUID, BigDecimal> quantitiesForEmployee, UUID childAllowanceComponentId) {
		String key = comp.getCountryRuleKey();
		if (SurinameCountryRuleKeys.CHILD_ALLOWANCE.equals(key)
				|| SurinameCountryRuleKeys.WAGE_TAX_CHILD_ALLOWANCE.equals(key)) {
			return childAllowanceComponentId != null ? quantitiesForEmployee.get(childAllowanceComponentId) : null;
		}
		return quantitiesForEmployee.get(comp.getId());
	}

	private static int calendarYear(PayrollContext context) {
		LocalDate asOf = context.countryRulesAsOf() != null ? context.countryRulesAsOf()
				: LocalDate.now(ZoneOffset.UTC);
		return asOf.getYear();
	}

	private BigDecimal jubileeWageTax(boolean applyTaxes, SurinameSpecialRemunerationSupport.Amounts special,
			ResolvedSurinameTaxRule paymentsAtOnceTaxRule) {
		if (!applyTaxes || special == null || paymentsAtOnceTaxRule == null || special.jubileeTaxable() == null
				|| special.jubileeTaxable().signum() <= 0) {
			return BigDecimal.ZERO.setScale(4, java.math.RoundingMode.HALF_UP);
		}
		return wageTaxCalculator.computePaymentAtOnceTax(paymentsAtOnceTaxRule, special.jubileeTaxable());
	}

	private Map<UUID, Integer> serviceYearsByEmployee(PayrollContext context, LocalDate asOf) {
		if (context.employeeIds().isEmpty()) {
			return Map.of();
		}
		Map<UUID, Integer> out = new HashMap<>();
		for (TenantEmployeeEntity employee : employeeRepository.findByTenantIdAndCompanyIdAndIdIn(context.tenantId(),
				context.companyId(), context.employeeIds())) {
			out.put(employee.getId(), SurinameJubileeSupport.completedServiceYears(employee.getHireDate(), asOf));
		}
		return out;
	}

	private BigDecimal lumpSumWageTax(boolean applyTaxes, BigDecimal lumpSumPayout,
			ResolvedSurinameTaxRule paymentsAtOnceTaxRule) {
		if (!applyTaxes || lumpSumPayout == null || lumpSumPayout.signum() <= 0 || paymentsAtOnceTaxRule == null) {
			return BigDecimal.ZERO.setScale(4, java.math.RoundingMode.HALF_UP);
		}
		return wageTaxCalculator.computePaymentAtOnceTax(paymentsAtOnceTaxRule, lumpSumPayout);
	}

	private BigDecimal overtimeWageTax(boolean applyTaxes, BigDecimal overtimePayout,
			ResolvedSurinameTaxRule overtimeTaxRule, int periodsPerYear) {
		if (!applyTaxes || overtimePayout == null || overtimePayout.signum() <= 0 || overtimeTaxRule == null) {
			return BigDecimal.ZERO.setScale(4, java.math.RoundingMode.HALF_UP);
		}
		return wageTaxCalculator.computePeriodTax(overtimeTaxRule, overtimePayout, periodsPerYear);
	}

	private BigDecimal aovPremium(boolean applyAov, BigDecimal payout, ResolvedSurinameTaxRule aovRule, int periodsPerYear) {
		if (!applyAov || payout == null || payout.signum() <= 0 || aovRule == null) {
			return BigDecimal.ZERO.setScale(4, java.math.RoundingMode.HALF_UP);
		}
		return wageTaxCalculator.computePeriodTax(aovRule, payout, periodsPerYear);
	}

	private BigDecimal art17WageTax(BigDecimal taxablePayout, BigDecimal labelPeriodWage,
			ResolvedSurinameTaxRule wageTaxRule, int periodsPerYear) {
		if (taxablePayout == null || taxablePayout.signum() <= 0 || wageTaxRule == null) {
			return BigDecimal.ZERO.setScale(4, java.math.RoundingMode.HALF_UP);
		}
		return wageTaxCalculator.computeArt17BijzondereBeloningTax(taxablePayout,
				SurinameSpecialRemunerationSupport.DEFAULT_ATTRIBUTION_PERIODS, labelPeriodWage, wageTaxRule,
				periodsPerYear);
	}

	private Map<UUID, TenantEmployeeCompensationEntity> loadCompensation(PayrollContext context) {
		if (context.employeeIds().isEmpty()) {
			return Map.of();
		}
		return compensationRepository.findByTenantIdAndEmployeeIdIn(context.tenantId(), context.employeeIds()).stream()
				.collect(java.util.stream.Collectors.toMap(TenantEmployeeCompensationEntity::getEmployeeId, c -> c,
						(a, b) -> a));
	}

	private static String lineKey(UUID employeeId, UUID componentId) {
		return employeeId + ":" + componentId;
	}

}
