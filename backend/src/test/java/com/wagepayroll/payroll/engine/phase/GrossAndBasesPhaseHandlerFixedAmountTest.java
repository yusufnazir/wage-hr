package com.wagepayroll.payroll.engine.phase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.wagepayroll.domain.compensation.TenantEmployeeCompensationRepository;
import com.wagepayroll.domain.org.TenantCompanyRepository;
import com.wagepayroll.domain.org.TenantDepartmentRepository;
import com.wagepayroll.domain.org.TenantEmployeeEntity;
import com.wagepayroll.domain.org.TenantEmployeeRepository;
import com.wagepayroll.domain.org.TenantJobRepository;
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
import com.wagepayroll.payroll.engine.EvaluatedComponentAmount;
import com.wagepayroll.payroll.engine.PayrollContext;
import com.wagepayroll.payroll.engine.PayrollRunState;
import com.wagepayroll.payroll.formula.FormulaDefinitionSupport;
import com.wagepayroll.payroll.formula.FormulaRuleResolver;
import com.wagepayroll.payroll.formula.WageComponentFormulaEvaluator;
import com.wagepayroll.payroll.model.CalculationMethod;
import com.wagepayroll.payroll.model.ComponentType;
import com.wagepayroll.payroll.model.NetEffect;
import com.wagepayroll.payrollstanding.TenantPayrollPeriodInputService;

@ExtendWith(MockitoExtension.class)
class GrossAndBasesPhaseHandlerFixedAmountTest {

	private static final UUID TENANT = UUID.randomUUID();
	private static final UUID COMPANY = UUID.randomUUID();
	private static final UUID PERIOD = UUID.randomUUID();
	private static final UUID EMPLOYEE = UUID.randomUUID();
	private static final UUID VACATION_ID = UUID.randomUUID();

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
	private FormulaDefinitionSupport formulaDefinitionSupport;
	@Mock
	private FormulaRuleResolver formulaRuleResolver;
	@Mock
	private WageComponentFormulaEvaluator wageComponentFormulaEvaluator;
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
		handler = new GrossAndBasesPhaseHandler(tenantWageComponentRepository, tenantWageComponentTransactionRepository,
				tenantWageComponentDependencyRepository, tenantEmployeeCompensationRepository, tenantCompanyRepository,
				tenantWorkTimeRepository, tenantEmployeeRepository, tenantDepartmentRepository, tenantJobRepository,
				formulaDefinitionSupport, formulaRuleResolver, wageComponentFormulaEvaluator, payrollBaseAccumulator,
				componentExecutionOrderService, surinameDerivedComponentService, payrollPeriodInputService);
		when(surinameDerivedComponentService.supports(any())).thenReturn(false);
	}

	@Test
	void fixedAmountUsesMaterializedTransactionWhenDefaultIsZero() {
		TenantWageComponentEntity vacation = component(VACATION_ID, "1006", CalculationMethod.FIXED_AMOUNT,
				BigDecimal.ZERO);
		when(tenantWageComponentRepository.findByTenantIdAndCompanyIdAndActiveIsTrueOrderByProcessingOrderAsc(TENANT,
				COMPANY)).thenReturn(List.of(vacation));
		when(tenantWageComponentDependencyRepository.findByTenantIdTouchingComponents(TENANT, List.of(VACATION_ID)))
				.thenReturn(List.of());
		when(componentExecutionOrderService.sortForExecution(List.of(vacation), List.of())).thenReturn(List.of(vacation));

		TenantWageComponentTransactionEntity tx = new TenantWageComponentTransactionEntity();
		tx.setEmployeeId(EMPLOYEE);
		tx.setTenantWageComponentId(VACATION_ID);
		tx.setAmount(new BigDecimal("500.0000"));
		when(tenantWageComponentTransactionRepository.findByTenantIdAndCompanyIdAndPayPeriodIdAndEmployeeIdIn(TENANT,
				COMPANY, PERIOD, List.of(EMPLOYEE))).thenReturn(List.of(tx));
		TenantEmployeeEntity employee = new TenantEmployeeEntity();
		employee.setId(EMPLOYEE);
		when(tenantEmployeeRepository.findByTenantIdAndCompanyIdAndIdIn(TENANT, COMPANY, List.of(EMPLOYEE)))
				.thenReturn(List.of(employee));
		when(tenantEmployeeCompensationRepository.findByTenantIdAndEmployeeIdIn(TENANT, List.of(EMPLOYEE)))
				.thenReturn(List.of());
		when(payrollBaseAccumulator.accumulateForEmployees(any(), any())).thenReturn(Map.of());
		when(payrollBaseAccumulator.accumulateDetailed(any(), any()))
				.thenReturn(PayrollBaseAccumulationResult.of(Map.of(), Map.of()));

		var ctx = new PayrollContext(TENANT, COMPANY, "SR", "SRD", null, PERIOD, List.of(EMPLOYEE),
				java.time.LocalDate.of(2026, 2, 28));
		PayrollRunState state = new PayrollRunState(ctx);
		handler.execute(state);

		assertThat(state.evaluatedComponentAmounts()).anySatisfy(line -> {
			assertThat(line.tenantWageComponentCode()).isEqualTo("1006");
			assertThat(line.evaluatedAmount()).isEqualByComparingTo("500.0000");
		});
	}

	private static TenantWageComponentEntity component(UUID id, String code, CalculationMethod method,
			BigDecimal defaultAmount) {
		TenantWageComponentEntity c = new TenantWageComponentEntity();
		c.setId(id);
		c.setCode(code);
		c.setName(code);
		c.setComponentType(ComponentType.EARNING);
		c.setCategory("ALLOWANCE");
		c.setNetEffect(NetEffect.ADD_TO_NET);
		c.setProcessingOrder(1000);
		c.setCalculationMethod(method);
		c.setDefaultAmount(defaultAmount);
		c.setRoundingStrategy(com.wagepayroll.payroll.model.RoundingStrategy.HALF_UP);
		return c;
	}
}
