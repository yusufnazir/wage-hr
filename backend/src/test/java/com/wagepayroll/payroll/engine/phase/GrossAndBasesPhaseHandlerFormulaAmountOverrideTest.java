package com.wagepayroll.payroll.engine.phase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wagepayroll.domain.compensation.TenantEmployeeCompensationEntity;
import com.wagepayroll.domain.compensation.TenantEmployeeCompensationRepository;
import com.wagepayroll.domain.org.TenantCompanyEntity;
import com.wagepayroll.domain.org.TenantCompanyRepository;
import com.wagepayroll.domain.org.TenantDepartmentRepository;
import com.wagepayroll.domain.org.TenantEmployeeEntity;
import com.wagepayroll.domain.org.TenantEmployeeRepository;
import com.wagepayroll.domain.org.TenantJobRepository;
import com.wagepayroll.domain.org.TenantWorkTimeEntity;
import com.wagepayroll.domain.org.TenantWorkTimeRepository;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentDependencyRepository;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentEntity;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentRepository;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentTransactionEntity;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentTransactionRepository;
import com.wagepayroll.payroll.base.PayrollBaseAccumulationResult;
import com.wagepayroll.payroll.base.PayrollBaseAccumulator;
import com.wagepayroll.payroll.country.SurinameTenantDerivedComponentService;
import com.wagepayroll.payroll.engine.ComponentExecutionOrderService;
import com.wagepayroll.payroll.engine.PayrollContext;
import com.wagepayroll.payroll.engine.PayrollRunState;
import com.wagepayroll.payroll.formula.CompensationFormulaSupport;
import com.wagepayroll.payroll.formula.FormulaDefinitionSupport;
import com.wagepayroll.payroll.formula.FormulaRuleResolver;
import com.wagepayroll.payroll.formula.WageComponentFormulaEvaluator;
import com.wagepayroll.payroll.formula.WageComponentFormulaValidator;
import com.wagepayroll.payroll.model.CalculationMethod;
import com.wagepayroll.payroll.model.ComponentType;
import com.wagepayroll.payroll.model.NetEffect;
import com.wagepayroll.payroll.trace.PayrollCalculationTraceLine;
import com.wagepayroll.payrollstanding.TenantPayrollPeriodInputService;

@ExtendWith(MockitoExtension.class)
class GrossAndBasesPhaseHandlerFormulaAmountOverrideTest {

	private static final UUID TENANT = UUID.randomUUID();
	private static final UUID COMPANY = UUID.randomUUID();
	private static final UUID PERIOD = UUID.randomUUID();
	private static final UUID EMPLOYEE = UUID.randomUUID();
	private static final UUID SALARY_ID = UUID.randomUUID();

	@Mock
	private TenantWageComponentRepository tenantWageComponentRepository;
	@Mock
	private TenantWageComponentTransactionRepository tenantWageComponentTransactionRepository;
	@Mock
	private TenantWageComponentDependencyRepository tenantWageComponentDependencyRepository;
	@Mock
	private TenantEmployeeCompensationRepository tenantEmployeeCompensationRepository;
	@Mock
	private TenantCompanyRepository tenantCompanyRepository;
	@Mock
	private TenantWorkTimeRepository tenantWorkTimeRepository;
	@Mock
	private TenantEmployeeRepository tenantEmployeeRepository;
	@Mock
	private TenantDepartmentRepository tenantDepartmentRepository;
	@Mock
	private TenantJobRepository tenantJobRepository;
	@Mock
	private PayrollBaseAccumulator payrollBaseAccumulator;
	@Mock
	private ComponentExecutionOrderService componentExecutionOrderService;
	@Mock
	private SurinameTenantDerivedComponentService surinameDerivedComponentService;
	@Mock
	private TenantPayrollPeriodInputService payrollPeriodInputService;

	private GrossAndBasesPhaseHandler handler;

	@BeforeEach
	void setUp() {
		ObjectMapper objectMapper = new ObjectMapper();
		var formulaDefinitionSupport = new FormulaDefinitionSupport(objectMapper,
				new WageComponentFormulaValidator(objectMapper));
		var formulaRuleResolver = new FormulaRuleResolver();
		var wageComponentFormulaEvaluator = new WageComponentFormulaEvaluator(objectMapper);
		handler = new GrossAndBasesPhaseHandler(tenantWageComponentRepository, tenantWageComponentTransactionRepository,
				tenantWageComponentDependencyRepository, tenantEmployeeCompensationRepository, tenantCompanyRepository,
				tenantWorkTimeRepository, tenantEmployeeRepository, tenantDepartmentRepository, tenantJobRepository,
				formulaDefinitionSupport, formulaRuleResolver, wageComponentFormulaEvaluator, payrollBaseAccumulator,
				componentExecutionOrderService, surinameDerivedComponentService, payrollPeriodInputService);
		when(surinameDerivedComponentService.supports(any())).thenReturn(false);
	}

	@Test
	void formulaComponentUsesStandingInstructionAmountOverrideInsteadOfPeriodicRate() {
		TenantWageComponentEntity salary = component(SALARY_ID, "1001", CompensationFormulaSupport.BASE_SALARY_FORMULA);
		when(tenantWageComponentRepository.findByTenantIdAndCompanyIdAndActiveIsTrueOrderByProcessingOrderAsc(TENANT,
				COMPANY)).thenReturn(List.of(salary));
		when(tenantWageComponentDependencyRepository.findByTenantIdTouchingComponents(TENANT, List.of(SALARY_ID)))
				.thenReturn(List.of());
		when(componentExecutionOrderService.sortForExecution(List.of(salary), List.of())).thenReturn(List.of(salary));

		TenantWageComponentTransactionEntity tx = new TenantWageComponentTransactionEntity();
		tx.setEmployeeId(EMPLOYEE);
		tx.setTenantWageComponentId(SALARY_ID);
		tx.setAmount(new BigDecimal("25000.0000"));
		tx.setManualOverride(false);
		when(tenantWageComponentTransactionRepository.findByTenantIdAndCompanyIdAndPayPeriodIdAndEmployeeIdIn(TENANT,
				COMPANY, PERIOD, List.of(EMPLOYEE))).thenReturn(List.of(tx));

		TenantCompanyEntity company = new TenantCompanyEntity();
		company.setId(COMPANY);
		company.setPayrollFrequency("MONTHLY");
		when(tenantCompanyRepository.findByIdAndTenantId(COMPANY, TENANT)).thenReturn(java.util.Optional.of(company));

		TenantEmployeeEntity employee = new TenantEmployeeEntity();
		employee.setId(EMPLOYEE);
		when(tenantEmployeeRepository.findByTenantIdAndCompanyIdAndIdIn(TENANT, COMPANY, List.of(EMPLOYEE)))
				.thenReturn(List.of(employee));

		TenantEmployeeCompensationEntity compensation = new TenantEmployeeCompensationEntity();
		compensation.setEmployeeId(EMPLOYEE);
		compensation.setWageType("PER_MONTH");
		compensation.setWageAmount(new BigDecimal("6900.0000"));
		UUID workTimeId = UUID.randomUUID();
		compensation.setWorkTimeId(workTimeId);
		when(tenantEmployeeCompensationRepository.findByTenantIdAndEmployeeIdIn(TENANT, List.of(EMPLOYEE)))
				.thenReturn(List.of(compensation));
		TenantWorkTimeEntity workTime = new TenantWorkTimeEntity();
		workTime.setId(workTimeId);
		workTime.setHoursPerDay(new BigDecimal("8"));
		workTime.setWorkDaysPerWeek(5);
		when(tenantWorkTimeRepository.findByTenantIdAndCompanyIdAndIdIn(eq(TENANT), eq(COMPANY), any()))
				.thenReturn(List.of(workTime));
		when(payrollBaseAccumulator.accumulateForEmployees(any(), any())).thenReturn(Map.of());
		when(payrollBaseAccumulator.accumulateDetailed(any(), any()))
				.thenReturn(PayrollBaseAccumulationResult.of(Map.of(), Map.of()));

		var ctx = new PayrollContext(TENANT, COMPANY, "SR", "SRD", null, PERIOD, List.of(EMPLOYEE),
				LocalDate.of(2026, 2, 28));
		PayrollRunState state = new PayrollRunState(ctx);
		handler.execute(state);

		assertThat(state.evaluatedComponentAmounts()).anySatisfy(line -> {
			assertThat(line.tenantWageComponentCode()).isEqualTo("1001");
			assertThat(line.evaluatedAmount()).isEqualByComparingTo("25000.0000");
		});

		PayrollCalculationTraceLine trace = state.calculationTrace().linesByEmployee().get(EMPLOYEE).stream()
				.filter(line -> "1001".equals(line.componentCode())).findFirst().orElseThrow();
		assertThat(trace.amountExplanation()).contains("Standing instruction amount override");
		assertThat(trace.amountExplanation()).contains("25000");
		assertThat(trace.amountExplanation()).contains("6900");
		assertThat(trace.factorExplanation()).contains("Standing instruction amount override");
	}

	private static TenantWageComponentEntity component(UUID id, String code, String formula) {
		TenantWageComponentEntity c = new TenantWageComponentEntity();
		c.setId(id);
		c.setCode(code);
		c.setName("Base salary");
		c.setComponentType(ComponentType.EARNING);
		c.setCategory("SALARY");
		c.setNetEffect(NetEffect.ADD_TO_NET);
		c.setTaxableWageTax(true);
		c.setCalculationMethod(CalculationMethod.FORMULA);
		c.setFormulaExpression(formula);
		c.setDefaultAmount(BigDecimal.ZERO);
		c.setRoundingStrategy(com.wagepayroll.payroll.model.RoundingStrategy.HALF_UP);
		c.setProcessingOrder(1010);
		return c;
	}
}
