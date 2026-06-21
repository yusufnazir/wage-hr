package com.wagepayroll.payroll.country;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.wagepayroll.domain.compensation.TenantEmployeeCompensationEntity;
import com.wagepayroll.domain.compensation.TenantEmployeeCompensationRepository;
import com.wagepayroll.domain.org.TenantCompanyEntity;
import com.wagepayroll.domain.org.TenantCompanyRepository;
import com.wagepayroll.domain.wagecomponent.PlatformWageComponentEntity;
import com.wagepayroll.domain.wagecomponent.PlatformWageComponentRepository;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentTransactionEntity;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentTransactionRepository;
import com.wagepayroll.payroll.engine.EvaluatedComponentAmount;
import com.wagepayroll.payroll.engine.PayrollRunState;
import com.wagepayroll.payroll.formula.CompensationFormulaSupport;
import com.wagepayroll.payroll.model.NetEffect;
import com.wagepayroll.payroll.base.PayrollBaseContribution;
import com.wagepayroll.payroll.trace.PayrollCalculationTraceSupport;

@Component
public class SurinameStatutoryContributor implements CountryStatutoryContributor {

	private static final String SR = "SR";

	private static final String PRIMARY_WAGE_TAX_RULE = "SR_WAGE_TAX_DEFAULT";

	private static final String AOV_RULE = "SR_AOV_PREMIUM_MONTH";

	private static final String LOONBELASTING_BASE = "LOONBELASTING";

	private static final String AOV_BASE = "AOV";

	private static final String WAGE_TAX_COMPONENT_CODE = "WAGE_TAX";

	private static final String SOCIAL_PREMIUM_EE_CODE = "SOCIAL_PREMIUM_EE";

	private final SurinameWageTaxCalculator wageTaxCalculator;

	private final SurinameCountryRuleAlgorithms countryRuleAlgorithms;

	private final PlatformWageComponentRepository platformWageComponentRepository;

	private final TenantEmployeeCompensationRepository compensationRepository;

	private final TenantCompanyRepository companyRepository;

	private final TenantWageComponentTransactionRepository transactionRepository;

	public SurinameStatutoryContributor(SurinameWageTaxCalculator wageTaxCalculator,
			SurinameCountryRuleAlgorithms countryRuleAlgorithms,
			PlatformWageComponentRepository platformWageComponentRepository,
			TenantEmployeeCompensationRepository compensationRepository,
			TenantCompanyRepository companyRepository,
			TenantWageComponentTransactionRepository transactionRepository) {
		this.wageTaxCalculator = wageTaxCalculator;
		this.countryRuleAlgorithms = countryRuleAlgorithms;
		this.platformWageComponentRepository = platformWageComponentRepository;
		this.compensationRepository = compensationRepository;
		this.companyRepository = companyRepository;
		this.transactionRepository = transactionRepository;
	}

	@Override
	public boolean supports(String payrollCountryIso2) {
		return SR.equalsIgnoreCase(payrollCountryIso2);
	}

	@Override
	public void contribute(PayrollRunState state) {
		if (state.context().employeeIds().isEmpty()) {
			return;
		}
		Optional<SurinameTaxRulesSnapshot> snapshotOpt = state.countryRuleContext()
				.findAttribute(SurinameCountryContextKeys.TAX_RULES_SNAPSHOT, SurinameTaxRulesSnapshot.class);
		if (snapshotOpt.isEmpty()) {
			return;
		}
		SurinameTaxRulesSnapshot snapshot = snapshotOpt.get();
		Map<String, PlatformWageComponentEntity> platformByCode = indexPlatformComponents(state.context().payrollCountryIso2());
		ResolvedSurinameTaxRule wageTaxRule = snapshot.rulesByCode().get(PRIMARY_WAGE_TAX_RULE);
		ResolvedSurinameTaxRule aovRule = snapshot.rulesByCode().get(AOV_RULE);
		PlatformWageComponentEntity wageTaxComponent = platformByCode.get(WAGE_TAX_COMPONENT_CODE);
		PlatformWageComponentEntity aovComponent = platformByCode.get(SOCIAL_PREMIUM_EE_CODE);
		Map<UUID, TenantEmployeeCompensationEntity> compensationByEmployee = loadCompensationByEmployee(state);
		TenantCompanyEntity company = companyRepository
				.findByIdAndTenantId(state.context().companyId(), state.context().tenantId()).orElse(null);
		int periods = SurinameWageTaxCalculator.DEFAULT_PERIODS_PER_YEAR;
		Map<UUID, BigDecimal> referenceMonthWageByEmployee = referenceMonthWages(state, compensationByEmployee, company);
		Map<UUID, SurinameSpecialRemunerationSupport.Amounts> specialByEmployee = SurinameSpecialRemunerationSupport
				.amountsByEmployee(state.context().employeeIds(), state.evaluatedComponentAmounts(),
						state.employeeBaseTotals(), referenceMonthWageByEmployee, Map.of(), snapshot, periods);

		for (UUID employeeId : state.context().employeeIds()) {
			TenantEmployeeCompensationEntity compensation = compensationByEmployee.get(employeeId);
			boolean applyTaxes = compensation == null || compensation.isApplyTaxes();
			boolean applyAov = compensation == null || compensation.isApplyAov();
			Map<String, BigDecimal> bases = state.employeeBaseTotals().getOrDefault(employeeId, Map.of());
			SurinameSpecialRemunerationSupport.Amounts special = specialByEmployee.getOrDefault(employeeId,
					SurinameSpecialRemunerationSupport.compute(bases.getOrDefault(LOONBELASTING_BASE, BigDecimal.ZERO),
							BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
							BigDecimal.ZERO, referenceMonthWageByEmployee.getOrDefault(employeeId, BigDecimal.ZERO),
							null, snapshot, periods));
			if (applyTaxes && wageTaxRule != null && wageTaxComponent != null) {
				boolean applyTaxExempt = compensation == null || compensation.isApplyTaxExempt();
				BigDecimal childAllowanceChildren = childAllowanceChildrenCount(state, employeeId);
				BigDecimal exchangeRatePayout = exchangeRateCompensationPayout(state, employeeId);
				BigDecimal labelWage = special.labelPeriodWage();
				BigDecimal childExclusion = childAllowanceChildren != null && childAllowanceChildren.signum() > 0
						? countryRuleAlgorithms.periodChildAllowanceExcludedFromLoon(snapshot, childAllowanceChildren)
						: BigDecimal.ZERO;
				BigDecimal exchangeRateExclusion = exchangeRatePayout != null && exchangeRatePayout.signum() > 0
						? countryRuleAlgorithms.periodExchangeRateCompensationExcludedFromLoon(snapshot,
								exchangeRatePayout)
						: BigDecimal.ZERO;
				BigDecimal grossBase = bases.getOrDefault(SurinameCountryRuleAlgorithms.GROSS_BASE, labelWage);
				BigDecimal taxExemptApplied = applyTaxExempt
						? countryRuleAlgorithms.periodTaxExemptApplied(snapshot, true, periods, labelWage)
						: BigDecimal.ZERO;
				BigDecimal taxableWithoutDeductible = countryRuleAlgorithms.adjustTaxableBaseForWageTax(labelWage, snapshot,
						applyTaxExempt, periods, childAllowanceChildren, null, exchangeRatePayout);
				BigDecimal deductibleApplied = countryRuleAlgorithms.periodDeductibleCostsApplied(taxableWithoutDeductible,
						grossBase, snapshot, periods);
				BigDecimal taxable = countryRuleAlgorithms.adjustTaxableBaseForWageTax(labelWage, snapshot, applyTaxExempt,
						periods, childAllowanceChildren, grossBase, exchangeRatePayout);
				BigDecimal tax = wageTaxCalculator.computePeriodTax(wageTaxRule, taxable, periods);
				Map<String, List<PayrollBaseContribution>> contributionsByBase = state.employeeBaseContributions()
						.getOrDefault(employeeId, Map.of());
				String loonbelastingBreakdown = PayrollCalculationTraceSupport.formatBaseBreakdownFromMap(
						contributionsByBase, LOONBELASTING_BASE,
						bases.getOrDefault(LOONBELASTING_BASE, BigDecimal.ZERO));
				String labelWageBreakdown = PayrollCalculationTraceSupport
						.formatLabelPeriodWageBreakdown(bases.getOrDefault(LOONBELASTING_BASE, BigDecimal.ZERO), special);
				String wageTaxFactorExplanation = PayrollCalculationTraceSupport.appendBreakdown(
						"Label period wage = LOONBELASTING "
								+ PayrollCalculationTraceSupport.formatMoney(bases.getOrDefault(LOONBELASTING_BASE, BigDecimal.ZERO))
								+ " − vacation " + PayrollCalculationTraceSupport.formatMoney(special.vacationPayout())
								+ " − bonus " + PayrollCalculationTraceSupport.formatMoney(special.bonusPayout())
								+ " − overtime " + PayrollCalculationTraceSupport.formatMoney(special.overtimePayout())
								+ " − lump sum " + PayrollCalculationTraceSupport.formatMoney(special.lumpSumPayout())
								+ " − jubilee " + PayrollCalculationTraceSupport.formatMoney(special.jubileePayout())
								+ " − extra earnings "
								+ PayrollCalculationTraceSupport.formatMoney(special.extraEarningsPayout()) + " = "
								+ PayrollCalculationTraceSupport.formatMoney(labelWage) + ".",
						labelWageBreakdown);
				String wageTaxAmountExplanation = PayrollCalculationTraceSupport.appendBreakdown(wageTaxFactorExplanation
						+ " Then: − child exclusion " + PayrollCalculationTraceSupport.formatMoney(childExclusion)
						+ ", − exchange-rate exclusion " + PayrollCalculationTraceSupport.formatMoney(exchangeRateExclusion)
						+ ", − belastingvrij " + PayrollCalculationTraceSupport.formatMoney(taxExemptApplied)
						+ ", − beroepskosten (1036, 4% of gross, max 400/mo) "
						+ PayrollCalculationTraceSupport.formatMoney(deductibleApplied) + " → taxable base "
						+ PayrollCalculationTraceSupport.formatMoney(taxable)
						+ ". Progressive wage tax (SR_WAGE_TAX_DEFAULT, annual ladder ÷ 12) → "
						+ PayrollCalculationTraceSupport.formatMoney(tax) + ".", loonbelastingBreakdown);
				boolean included = tax.signum() > 0;
				state.calculationTrace().addPlatformStatutory("STATUTORY", employeeId, WAGE_TAX_COMPONENT_CODE,
						"Wage tax (template 1019)", wageTaxComponent.getProcessingOrder(), NetEffect.SUBTRACT_FROM_NET,
						"TAX", wageTaxComponent.getCalculationMethod().name(), tax, wageTaxFactorExplanation,
						wageTaxAmountExplanation, included,
						included ? null
								: "Tax is zero after belastingvrij and exclusions — main wage tax line omitted (special payments taxed on lines 1020–1025).");
				if (included) {
					state.statutoryEvaluatedAmounts().add(EvaluatedComponentAmount.platform(employeeId,
							wageTaxComponent.getId(), wageTaxComponent.getCode(),
							wageTaxComponent.getCalculationMethod().name(), tax));
				}
			}
			else if (!applyTaxes) {
				state.calculationTrace().addPlatformStatutory("STATUTORY", employeeId, WAGE_TAX_COMPONENT_CODE,
						"Wage tax (template 1019)", 5210, NetEffect.SUBTRACT_FROM_NET, "TAX", "STATUTORY", BigDecimal.ZERO,
						"Employee compensation setting: Apply taxes = no.", "Wage tax skipped.", false,
						"Apply taxes is disabled on employee compensation.");
			}
			if (applyAov && aovRule != null && aovComponent != null) {
				BigDecimal aovBase = bases.getOrDefault(AOV_BASE, BigDecimal.ZERO);
				BigDecimal labelAovBase = aovBase.subtract(special.vacationPayout()).subtract(special.bonusPayout())
						.subtract(special.overtimePayout()).subtract(special.lumpSumPayout())
						.subtract(special.jubileePayout()).subtract(special.extraEarningsPayout());
				if (labelAovBase.signum() < 0) {
					labelAovBase = BigDecimal.ZERO;
				}
				BigDecimal aov = wageTaxCalculator.computePeriodTax(aovRule, labelAovBase,
						SurinameWageTaxCalculator.DEFAULT_PERIODS_PER_YEAR);
				String aovFactorExplanation = "AOV base " + PayrollCalculationTraceSupport.formatMoney(aovBase)
						+ " − special payouts = label AOV base "
						+ PayrollCalculationTraceSupport.formatMoney(labelAovBase) + ".";
				String aovAmountExplanation = aovFactorExplanation + " Flat 4% month rule (SR_AOV_PREMIUM_MONTH) → "
						+ PayrollCalculationTraceSupport.formatMoney(aov) + ".";
				boolean aovIncluded = aov.signum() > 0;
				state.calculationTrace().addPlatformStatutory("STATUTORY", employeeId, SOCIAL_PREMIUM_EE_CODE,
						"AOV premium (template 1012)", aovComponent.getProcessingOrder(), NetEffect.SUBTRACT_FROM_NET,
						"SOCIAL_SECURITY", aovComponent.getCalculationMethod().name(), aov, aovFactorExplanation,
						aovAmountExplanation, aovIncluded, aovIncluded ? null : "AOV premium rounds to zero.");
				if (aovIncluded) {
					state.statutoryEvaluatedAmounts().add(EvaluatedComponentAmount.platform(employeeId,
							aovComponent.getId(), aovComponent.getCode(), aovComponent.getCalculationMethod().name(),
							aov));
				}
			}
			else if (!applyAov) {
				state.calculationTrace().addPlatformStatutory("STATUTORY", employeeId, SOCIAL_PREMIUM_EE_CODE,
						"AOV premium (template 1012)", 5070, NetEffect.SUBTRACT_FROM_NET, "SOCIAL_SECURITY", "STATUTORY",
						BigDecimal.ZERO, "Employee compensation setting: Apply AOV = no.", "AOV premium skipped.", false,
						"Apply AOV is disabled on employee compensation.");
			}
		}
	}

	private Map<UUID, TenantEmployeeCompensationEntity> loadCompensationByEmployee(PayrollRunState state) {
		if (state.context().employeeIds().isEmpty()) {
			return Map.of();
		}
		return compensationRepository
				.findByTenantIdAndEmployeeIdIn(state.context().tenantId(), state.context().employeeIds()).stream()
				.collect(java.util.stream.Collectors.toMap(TenantEmployeeCompensationEntity::getEmployeeId, c -> c,
						(a, b) -> a));
	}

	private Map<UUID, BigDecimal> referenceMonthWages(PayrollRunState state,
			Map<UUID, TenantEmployeeCompensationEntity> compensationByEmployee, TenantCompanyEntity company) {
		Map<UUID, BigDecimal> out = new HashMap<>();
		for (UUID employeeId : state.context().employeeIds()) {
			TenantEmployeeCompensationEntity compensation = compensationByEmployee.get(employeeId);
			if (compensation != null && compensation.getWageAmount() != null && company != null) {
				out.put(employeeId, CompensationFormulaSupport.periodAmount(compensation, company));
			}
		}
		return out;
	}

	private BigDecimal childAllowanceChildrenCount(PayrollRunState state, UUID employeeId) {
		UUID componentId = state.evaluatedComponentAmounts().stream()
				.filter(line -> employeeId.equals(line.employeeId()) && "1008".equals(line.tenantWageComponentCode()))
				.map(EvaluatedComponentAmount::tenantWageComponentId)
				.findFirst()
				.orElse(null);
		if (componentId == null || state.context().payPeriodId() == null) {
			return null;
		}
		var ctx = state.context();
		return transactionRepository
				.findByTenantIdAndCompanyIdAndPayPeriodIdAndEmployeeIdIn(ctx.tenantId(), ctx.companyId(),
						ctx.payPeriodId(), List.of(employeeId))
				.stream()
				.filter(tx -> componentId.equals(tx.getTenantWageComponentId()) && tx.getQuantity() != null
						&& tx.getQuantity().signum() > 0)
				.map(TenantWageComponentTransactionEntity::getQuantity)
				.findFirst()
				.orElse(null);
	}

	private BigDecimal exchangeRateCompensationPayout(PayrollRunState state, UUID employeeId) {
		UUID componentId = state.evaluatedComponentAmounts().stream()
				.filter(line -> employeeId.equals(line.employeeId()) && "1055".equals(line.tenantWageComponentCode()))
				.map(EvaluatedComponentAmount::tenantWageComponentId)
				.findFirst()
				.orElse(null);
		if (componentId == null || state.context().payPeriodId() == null) {
			return null;
		}
		var ctx = state.context();
		return transactionRepository
				.findByTenantIdAndCompanyIdAndPayPeriodIdAndEmployeeIdIn(ctx.tenantId(), ctx.companyId(),
						ctx.payPeriodId(), List.of(employeeId))
				.stream()
				.filter(tx -> componentId.equals(tx.getTenantWageComponentId()) && tx.getAmount() != null
						&& tx.getAmount().signum() > 0)
				.map(TenantWageComponentTransactionEntity::getAmount)
				.findFirst()
				.orElse(null);
	}

	private Map<String, PlatformWageComponentEntity> indexPlatformComponents(String countryCode) {
		return platformWageComponentRepository.findByCountryCodeAndActiveIsTrueOrderByProcessingOrderAsc(countryCode)
				.stream()
				.collect(java.util.stream.Collectors.toMap(PlatformWageComponentEntity::getCode, p -> p, (a, b) -> a));
	}
}
