package com.wagepayroll.payroll.engine.phase;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.wagepayroll.domain.compensation.TenantEmployeeCompensationEntity;
import com.wagepayroll.domain.compensation.TenantEmployeeCompensationRepository;
import com.wagepayroll.domain.org.TenantCompanyEntity;
import com.wagepayroll.domain.org.TenantCompanyRepository;
import com.wagepayroll.domain.org.TenantDepartmentEntity;
import com.wagepayroll.domain.org.TenantDepartmentRepository;
import com.wagepayroll.domain.org.TenantEmployeeEntity;
import com.wagepayroll.domain.org.TenantEmployeeRepository;
import com.wagepayroll.domain.org.TenantJobEntity;
import com.wagepayroll.domain.org.TenantJobRepository;
import com.wagepayroll.domain.org.TenantWorkTimeEntity;
import com.wagepayroll.domain.org.TenantWorkTimeRepository;
import com.wagepayroll.payroll.formula.CompensationFormulaSupport.CompensationBindings;
import com.wagepayroll.payroll.formula.CompensationFormulaSupport;
import com.wagepayroll.payroll.formula.EmployeeFormulaMatchContext;
import com.wagepayroll.payroll.formula.FormulaDefinitionConfig;
import com.wagepayroll.payroll.formula.FormulaDefinitionSupport;
import com.wagepayroll.payroll.formula.FormulaRuleResolver;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentDependencyEntity;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentDependencyRepository;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentEntity;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentRepository;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentTransactionEntity;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentTransactionRepository;
import com.wagepayroll.payroll.base.PayrollBaseAccumulationResult;
import com.wagepayroll.payroll.base.PayrollBaseAccumulator;
import com.wagepayroll.payroll.country.SurinameCountryRuleKeys;
import com.wagepayroll.payroll.trace.PayrollCalculationTraceSupport;
import com.wagepayroll.payroll.country.SurinameTenantDerivedComponentService;
import com.wagepayroll.payroll.engine.ComponentExecutionOrderService;
import com.wagepayroll.payroll.engine.EvaluatedComponentAmount;
import com.wagepayroll.payroll.engine.PayrollRunPhase;
import com.wagepayroll.payroll.engine.PayrollRunState;
import com.wagepayroll.payroll.formula.FormulaEvaluationContext;
import com.wagepayroll.payroll.formula.PayrollRounding;
import com.wagepayroll.payroll.formula.WageComponentFormulaEvaluator;
import com.wagepayroll.payroll.formula.WageComponentFormulaValidator;
import com.wagepayroll.payroll.model.CalculationMethod;
import com.wagepayroll.payrollstanding.TenantPayrollPeriodInputService;

@Component
public class GrossAndBasesPhaseHandler implements PayrollPhaseHandler {

	private static final MathContext MC = MathContext.DECIMAL64;

	private final TenantWageComponentRepository tenantWageComponentRepository;

	private final TenantWageComponentTransactionRepository tenantWageComponentTransactionRepository;

	private final TenantWageComponentDependencyRepository tenantWageComponentDependencyRepository;

	private final TenantEmployeeCompensationRepository tenantEmployeeCompensationRepository;

	private final TenantCompanyRepository tenantCompanyRepository;

	private final TenantWorkTimeRepository tenantWorkTimeRepository;

	private final TenantEmployeeRepository tenantEmployeeRepository;

	private final TenantDepartmentRepository tenantDepartmentRepository;

	private final TenantJobRepository tenantJobRepository;

	private final FormulaDefinitionSupport formulaDefinitionSupport;

	private final FormulaRuleResolver formulaRuleResolver;

	private final WageComponentFormulaEvaluator wageComponentFormulaEvaluator;

	private final PayrollBaseAccumulator payrollBaseAccumulator;

	private final ComponentExecutionOrderService componentExecutionOrderService;

	private final SurinameTenantDerivedComponentService surinameDerivedComponentService;

	private final TenantPayrollPeriodInputService payrollPeriodInputService;

	public GrossAndBasesPhaseHandler(TenantWageComponentRepository tenantWageComponentRepository,
			TenantWageComponentTransactionRepository tenantWageComponentTransactionRepository,
			TenantWageComponentDependencyRepository tenantWageComponentDependencyRepository,
			TenantEmployeeCompensationRepository tenantEmployeeCompensationRepository,
			TenantCompanyRepository tenantCompanyRepository, TenantWorkTimeRepository tenantWorkTimeRepository,
			TenantEmployeeRepository tenantEmployeeRepository, TenantDepartmentRepository tenantDepartmentRepository,
			TenantJobRepository tenantJobRepository, FormulaDefinitionSupport formulaDefinitionSupport,
			FormulaRuleResolver formulaRuleResolver, WageComponentFormulaEvaluator wageComponentFormulaEvaluator,
			PayrollBaseAccumulator payrollBaseAccumulator,
			ComponentExecutionOrderService componentExecutionOrderService,
			SurinameTenantDerivedComponentService surinameDerivedComponentService,
			TenantPayrollPeriodInputService payrollPeriodInputService) {
		this.tenantWageComponentRepository = tenantWageComponentRepository;
		this.tenantWageComponentTransactionRepository = tenantWageComponentTransactionRepository;
		this.tenantWageComponentDependencyRepository = tenantWageComponentDependencyRepository;
		this.tenantEmployeeCompensationRepository = tenantEmployeeCompensationRepository;
		this.tenantCompanyRepository = tenantCompanyRepository;
		this.tenantWorkTimeRepository = tenantWorkTimeRepository;
		this.tenantEmployeeRepository = tenantEmployeeRepository;
		this.tenantDepartmentRepository = tenantDepartmentRepository;
		this.tenantJobRepository = tenantJobRepository;
		this.formulaDefinitionSupport = formulaDefinitionSupport;
		this.formulaRuleResolver = formulaRuleResolver;
		this.wageComponentFormulaEvaluator = wageComponentFormulaEvaluator;
		this.payrollBaseAccumulator = payrollBaseAccumulator;
		this.componentExecutionOrderService = componentExecutionOrderService;
		this.surinameDerivedComponentService = surinameDerivedComponentService;
		this.payrollPeriodInputService = payrollPeriodInputService;
	}

	@Override
	public PayrollRunPhase phase() {
		return PayrollRunPhase.GROSS_AND_BASES;
	}

	@Override
	public void execute(PayrollRunState state) {
		var context = state.context();
		List<TenantWageComponentEntity> tenantComponents = tenantWageComponentRepository
				.findByTenantIdAndCompanyIdAndActiveIsTrueOrderByProcessingOrderAsc(context.tenantId(), context.companyId());
		state.setResolvedTenantComponentCount(tenantComponents.size());
		if (context.payPeriodId() == null || context.employeeIds().isEmpty() || tenantComponents.isEmpty()) {
			state.setEmployeeBaseTotals(Map.of());
			return;
		}
		payrollPeriodInputService.materializeForPayPeriod(context.tenantId(), context.companyId(),
				context.payPeriodId(), context.employeeIds(), null, "payroll-engine");
		List<UUID> componentIds = tenantComponents.stream().map(TenantWageComponentEntity::getId).toList();
		List<TenantWageComponentDependencyEntity> edges = tenantWageComponentDependencyRepository
				.findByTenantIdTouchingComponents(context.tenantId(), componentIds);
		List<TenantWageComponentEntity> executionOrder = componentExecutionOrderService.sortForExecution(tenantComponents,
				edges);
		List<EvaluatedComponentAmount> previews = evaluateTenantComponents(context, executionOrder, state);
		Map<UUID, Map<String, BigDecimal>> baseTotals = payrollBaseAccumulator.accumulateForEmployees(context.tenantId(),
				previews);
		if (surinameDerivedComponentService.supports(context.payrollCountryIso2())) {
			previews = surinameDerivedComponentService.applyDerivedLines(context, state.countryRuleContext(),
					executionOrder, previews, baseTotals, state);
		}
		PayrollBaseAccumulationResult finalBases = payrollBaseAccumulator.accumulateDetailed(context.tenantId(),
				previews);
		state.evaluatedComponentAmounts().addAll(previews);
		state.setEmployeeBaseTotals(finalBases.totalsByEmployee());
		state.setEmployeeBaseContributions(finalBases.contributionsByEmployee());
	}

	private List<EvaluatedComponentAmount> evaluateTenantComponents(com.wagepayroll.payroll.engine.PayrollContext context,
			List<TenantWageComponentEntity> tenantComponents, PayrollRunState state) {
		Map<String, TenantWageComponentTransactionEntity> txnByKey = loadTransactionsForEmployees(context);
		List<EvaluatedComponentAmount> out = new java.util.ArrayList<>();
		TenantCompanyEntity company = tenantCompanyRepository.findByIdAndTenantId(context.companyId(), context.tenantId())
				.orElse(null);
		Map<UUID, FormulaDefinitionConfig> formulaConfigByComponentId = new HashMap<>();
		for (TenantWageComponentEntity comp : tenantComponents) {
			if (comp.getCalculationMethod() == com.wagepayroll.payroll.model.CalculationMethod.FORMULA) {
				formulaConfigByComponentId.put(comp.getId(),
						formulaDefinitionSupport.parseStoredExpression(comp.getFormulaExpression()));
			}
		}
		Map<UUID, EmployeeEvaluationContext> evaluationByEmployee = loadEmployeeEvaluationContexts(context, company);
		for (UUID employeeId : context.employeeIds()) {
			EmployeeEvaluationContext evalCtx = evaluationByEmployee.get(employeeId);
			if (evalCtx == null) {
				continue;
			}
			Map<String, BigDecimal> amountsByCode = new HashMap<>();
			for (TenantWageComponentEntity comp : tenantComponents) {
				if (SurinameCountryRuleKeys.isDerivedAlgorithmKey(comp.getCountryRuleKey())
						|| SurinameCountryRuleKeys.isNetWageDisplayKey(comp.getCountryRuleKey())) {
					continue;
				}
				TenantWageComponentTransactionEntity tx = txnByKey.get(key(employeeId, comp.getId()));
				BigDecimal qty = tx != null && tx.getQuantity() != null ? tx.getQuantity() : BigDecimal.ZERO;
				BigDecimal rate = tx != null && tx.getRate() != null ? tx.getRate() : BigDecimal.ZERO;
				if (evalCtx.bindings().isHourly().compareTo(BigDecimal.ZERO) != 0 && rate.compareTo(BigDecimal.ZERO) == 0
						&& evalCtx.compensation() != null && evalCtx.compensation().getWageAmount() != null) {
					rate = evalCtx.compensation().getWageAmount();
				}
				BigDecimal txnAmt = tx != null && tx.getAmount() != null ? tx.getAmount() : BigDecimal.ZERO;
				BigDecimal defAmt = comp.getDefaultAmount() != null ? comp.getDefaultAmount() : BigDecimal.ZERO;
				var feCtx = new FormulaEvaluationContext(evalCtx.bindings().periodicRate(), evalCtx.bindings().isHourly(),
						evalCtx.bindings().hourlyRate(), qty, rate, txnAmt, defAmt, amountsByCode);
				var roundMode = PayrollRounding.toRoundingMode(comp.getRoundingStrategy());
				BigDecimal amount = null;
				String[] resolvedFormulaHolder = new String[1];
				BigDecimal[] formulaResultHolder = new BigDecimal[1];
				boolean[] manualTxnOverrideHolder = new boolean[1];
				boolean[] standingTxnOverrideHolder = new boolean[1];
				try {
					amount = switch (comp.getCalculationMethod()) {
						case FORMULA -> {
							FormulaDefinitionConfig formulaConfig = formulaConfigByComponentId.get(comp.getId());
							String expr = formulaRuleResolver.resolveExpression(formulaConfig, evalCtx.matchContext());
							resolvedFormulaHolder[0] = expr;
							if (expr == null || expr.isBlank()) {
								yield BigDecimal.ZERO.setScale(4, roundMode);
							}
							BigDecimal formulaResult = wageComponentFormulaEvaluator.evaluate(expr, feCtx, roundMode);
							formulaResultHolder[0] = formulaResult;
							manualTxnOverrideHolder[0] = PayrollCalculationTraceSupport
									.isManualTransactionAmountOverride(tx, txnAmt);
							standingTxnOverrideHolder[0] = !manualTxnOverrideHolder[0]
									&& PayrollCalculationTraceSupport.isStandingInstructionAmountOverride(tx, qty,
											txnAmt);
							if (manualTxnOverrideHolder[0] || standingTxnOverrideHolder[0]) {
								yield txnAmt.setScale(4, roundMode);
							}
							yield formulaResult;
						}
						case HOURLY -> qty.multiply(rate, MC).setScale(4, roundMode);
						case FIXED_AMOUNT -> (tx != null && txnAmt.signum() != 0 ? txnAmt : defAmt).setScale(4, roundMode);
						case MANUAL_INPUT -> txnAmt.setScale(4, roundMode);
						case PERCENTAGE -> null;
					};
				}
				catch (RuntimeException ex) {
					amount = BigDecimal.ZERO.setScale(4, roundMode);
				}
				if (amount != null) {
					String resolvedFormula = resolvedFormulaHolder[0];
					String amountExplanation = PayrollCalculationTraceSupport.tenantAmountExplanation(
							comp.getCalculationMethod(), qty, rate, txnAmt, defAmt, amount, formulaResultHolder[0],
							manualTxnOverrideHolder[0], standingTxnOverrideHolder[0]);
					if (comp.getCalculationMethod() == CalculationMethod.FORMULA && !manualTxnOverrideHolder[0]
							&& !standingTxnOverrideHolder[0]) {
						String dependencyBreakdown = PayrollCalculationTraceSupport.formatFormulaComponentDependencies(
								WageComponentFormulaValidator.extractComponentCodes(resolvedFormula), amountsByCode);
						amountExplanation = PayrollCalculationTraceSupport.appendBreakdown(amountExplanation,
								dependencyBreakdown);
					}
					state.calculationTrace().addTenantComponent("GROSS_AND_BASES", employeeId, comp, qty, rate,
							PayrollCalculationTraceSupport.tenantFactorExplanation(comp.getCalculationMethod(), qty, rate,
									txnAmt, defAmt, evalCtx.bindings().periodicRate(), evalCtx.bindings().hourlyRate(),
									resolvedFormula, manualTxnOverrideHolder[0], standingTxnOverrideHolder[0]),
							amount, amountExplanation,
							resolvedFormula != null ? resolvedFormula : comp.getFormulaExpression(), true, null);
					amountsByCode.put(comp.getCode(), amount);
					out.add(EvaluatedComponentAmount.tenant(employeeId, comp.getId(), comp.getCode(),
							comp.getCalculationMethod().name(), amount, comp.getFormulaExpression()));
				}
			}
		}
		return List.copyOf(out);
	}

	private Map<String, TenantWageComponentTransactionEntity> loadTransactionsForEmployees(
			com.wagepayroll.payroll.engine.PayrollContext context) {
		List<TenantWageComponentTransactionEntity> txRows = context.employeeIds().size() <= 50
				? tenantWageComponentTransactionRepository.findByTenantIdAndCompanyIdAndPayPeriodIdAndEmployeeIdIn(
						context.tenantId(), context.companyId(), context.payPeriodId(), context.employeeIds())
				: tenantWageComponentTransactionRepository.findByTenantIdAndCompanyIdAndPayPeriodId(context.tenantId(),
						context.companyId(), context.payPeriodId());
		Map<String, TenantWageComponentTransactionEntity> txnByKey = new HashMap<>();
		for (TenantWageComponentTransactionEntity row : txRows) {
			txnByKey.putIfAbsent(key(row.getEmployeeId(), row.getTenantWageComponentId()), row);
		}
		return txnByKey;
	}

	private Map<UUID, EmployeeEvaluationContext> loadEmployeeEvaluationContexts(
			com.wagepayroll.payroll.engine.PayrollContext context, TenantCompanyEntity company) {
		List<UUID> employeeIds = context.employeeIds();
		if (employeeIds.isEmpty()) {
			return Map.of();
		}
		Map<UUID, TenantEmployeeEntity> employees = tenantEmployeeRepository
				.findByTenantIdAndCompanyIdAndIdIn(context.tenantId(), context.companyId(), employeeIds).stream()
				.collect(Collectors.toMap(TenantEmployeeEntity::getId, Function.identity()));
		Map<UUID, TenantEmployeeCompensationEntity> compensations = tenantEmployeeCompensationRepository
				.findByTenantIdAndEmployeeIdIn(context.tenantId(), employeeIds).stream()
				.collect(Collectors.toMap(TenantEmployeeCompensationEntity::getEmployeeId, Function.identity(),
						(a, b) -> a));
		Set<UUID> departmentIds = new HashSet<>();
		Set<UUID> jobIds = new HashSet<>();
		Set<UUID> workTimeIds = new HashSet<>();
		for (TenantEmployeeEntity employee : employees.values()) {
			if (employee.getDepartmentId() != null) {
				departmentIds.add(employee.getDepartmentId());
			}
			if (employee.getJobId() != null) {
				jobIds.add(employee.getJobId());
			}
		}
		for (TenantEmployeeCompensationEntity compensation : compensations.values()) {
			if (compensation.getWorkTimeId() != null) {
				workTimeIds.add(compensation.getWorkTimeId());
			}
		}
		Map<UUID, String> departmentCodeById = departmentIds.isEmpty()
				? Map.of()
				: tenantDepartmentRepository.findByTenantIdAndIdIn(context.tenantId(), departmentIds).stream()
						.collect(Collectors.toMap(TenantDepartmentEntity::getId, TenantDepartmentEntity::getCode));
		Map<UUID, String> jobCodeById = jobIds.isEmpty()
				? Map.of()
				: tenantJobRepository.findByTenantIdAndIdIn(context.tenantId(), jobIds).stream()
						.collect(Collectors.toMap(TenantJobEntity::getId, TenantJobEntity::getCode));
		Map<UUID, TenantWorkTimeEntity> workTimeById = workTimeIds.isEmpty()
				? Map.of()
				: tenantWorkTimeRepository
						.findByTenantIdAndCompanyIdAndIdIn(context.tenantId(), context.companyId(), workTimeIds)
						.stream().collect(Collectors.toMap(TenantWorkTimeEntity::getId, Function.identity()));
		Map<UUID, EmployeeEvaluationContext> out = new HashMap<>();
		for (UUID employeeId : employeeIds) {
			TenantEmployeeEntity employee = employees.get(employeeId);
			if (employee == null) {
				continue;
			}
			String departmentCode = employee.getDepartmentId() != null
					? departmentCodeById.get(employee.getDepartmentId())
					: null;
			String jobCode = employee.getJobId() != null ? jobCodeById.get(employee.getJobId()) : null;
			TenantEmployeeCompensationEntity compensation = compensations.get(employeeId);
			TenantWorkTimeEntity workTime = compensation != null && compensation.getWorkTimeId() != null
					? workTimeById.get(compensation.getWorkTimeId())
					: null;
			CompensationBindings bindings = CompensationFormulaSupport.bindings(compensation, company, workTime);
			EmployeeFormulaMatchContext matchContext = compensation != null
					? new EmployeeFormulaMatchContext(compensation.getWageType(), departmentCode, jobCode)
					: new EmployeeFormulaMatchContext(null, departmentCode, jobCode);
			out.put(employeeId, new EmployeeEvaluationContext(compensation, bindings, matchContext));
		}
		return out;
	}

	private record EmployeeEvaluationContext(TenantEmployeeCompensationEntity compensation, CompensationBindings bindings,
			EmployeeFormulaMatchContext matchContext) {
	}

	private static String key(UUID employeeId, UUID componentId) {
		return employeeId + ":" + componentId;
	}
}
