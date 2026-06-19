package com.wagepayroll.payroll.country;

import static org.assertj.core.api.Assertions.assertThat;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;

import com.wagepayroll.domain.compensation.TenantEmployeeCompensationEntity;
import com.wagepayroll.domain.compensation.TenantEmployeeCompensationRepository;
import com.wagepayroll.domain.org.TenantCompanyEntity;
import com.wagepayroll.domain.org.TenantCompanyRepository;
import com.wagepayroll.domain.org.TenantEmployeeEntity;
import com.wagepayroll.domain.org.TenantEmployeeRepository;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentEntity;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentTransactionRepository;
import com.wagepayroll.payroll.base.PayrollBaseAccumulationResult;
import com.wagepayroll.payroll.base.PayrollBaseAccumulator;
import com.wagepayroll.payroll.engine.CountryRuleContext;
import com.wagepayroll.payroll.engine.EvaluatedComponentAmount;
import com.wagepayroll.payroll.engine.PayrollContext;
import com.wagepayroll.payroll.model.CalculationMethod;

@ExtendWith(MockitoExtension.class)
class SurinameTenantDerivedComponentServiceTest {

	private static final UUID EMPLOYEE = UUID.randomUUID();

	private static final UUID VACATION_AOV_COMPONENT = UUID.randomUUID();

	@Mock
	private SurinameCountryRuleAlgorithms algorithms;

	@Mock
	private TenantEmployeeCompensationRepository compensationRepository;

	@Mock
	private TenantCompanyRepository companyRepository;

	@Mock
	private TenantEmployeeRepository employeeRepository;

	@Mock
	private TenantWageComponentTransactionRepository transactionRepository;

	@Mock
	private PayrollBaseAccumulator payrollBaseAccumulator;

	private SurinameWageTaxCalculator wageTaxCalculator;

	private SurinameTenantDerivedComponentService service;

	@BeforeEach
	void setUp() {
		wageTaxCalculator = new SurinameWageTaxCalculator();
		service = new SurinameTenantDerivedComponentService(algorithms, wageTaxCalculator, new SurinameApfCalculator(),
				new SurinameFvoCalculator(wageTaxCalculator), payrollBaseAccumulator, compensationRepository,
				companyRepository, employeeRepository, transactionRepository);
	}

	@Test
	void appliesZeroJubileeWageTaxForScenario4() throws Exception {
		UUID jubileeTaxComponent = UUID.randomUUID();
		UUID tenantId = UUID.randomUUID();
		UUID companyId = UUID.randomUUID();
		TenantWageComponentEntity jubileeTax = component(jubileeTaxComponent, "1048",
				SurinameCountryRuleKeys.WAGE_TAX_JUBILEE);
		EvaluatedComponentAmount jubileeLine = EvaluatedComponentAmount.tenant(EMPLOYEE, UUID.randomUUID(), "1010",
				CalculationMethod.FIXED_AMOUNT.name(), new BigDecimal("6000.0000"), null);
		Map<UUID, Map<String, BigDecimal>> bases = Map.of(EMPLOYEE,
				Map.of(SurinameCountryRuleAlgorithms.LOONBELASTING_BASE, new BigDecimal("12000.0000")));
		when(payrollBaseAccumulator.accumulateForEmployees(any(), anyList())).thenReturn(bases);
		when(payrollBaseAccumulator.accumulateDetailed(any(), anyList()))
				.thenReturn(PayrollBaseAccumulationResult.of(bases, Map.of()));
		when(employeeRepository.findByTenantIdAndCompanyIdAndIdIn(tenantId, companyId, List.of(EMPLOYEE)))
				.thenReturn(List.of(employeeWithHireDate(EMPLOYEE, tenantId, companyId, LocalDate.of(2001, 2, 1))));
		stubMonthlyCompensation(tenantId, companyId, new BigDecimal("6000.0000"));
		SurinameTaxRulesSnapshot snapshot = snapshotWithJubileeRule();

		PayrollContext payroll = new PayrollContext(tenantId, companyId, "SR", "SRD", null, UUID.randomUUID(),
				List.of(EMPLOYEE), LocalDate.of(2026, 2, 28));
		List<EvaluatedComponentAmount> result = service.applyDerivedLines(payroll, countryContext(payroll, snapshot),
				List.of(jubileeTax), List.of(jubileeLine), bases, null);

		assertThat(result).anySatisfy(line -> {
			assertThat(line.tenantWageComponentId()).isEqualTo(jubileeTaxComponent);
			assertThat(line.tenantWageComponentCode()).isEqualTo("1048");
			assertThat(line.evaluatedAmount()).isEqualByComparingTo("0.0000");
		});
	}

	@Test
	void appliesJubileeWageTaxWhenPayoutExceedsArt10ExemptCap() throws Exception {
		UUID jubileeTaxComponent = UUID.randomUUID();
		UUID tenantId = UUID.randomUUID();
		UUID companyId = UUID.randomUUID();
		TenantWageComponentEntity jubileeTax = component(jubileeTaxComponent, "1048",
				SurinameCountryRuleKeys.WAGE_TAX_JUBILEE);
		EvaluatedComponentAmount jubileeLine = EvaluatedComponentAmount.tenant(EMPLOYEE, UUID.randomUUID(), "1010",
				CalculationMethod.FIXED_AMOUNT.name(), new BigDecimal("12000.0000"), null);
		Map<UUID, Map<String, BigDecimal>> bases = Map.of(EMPLOYEE,
				Map.of(SurinameCountryRuleAlgorithms.LOONBELASTING_BASE, new BigDecimal("18000.0000")));
		when(payrollBaseAccumulator.accumulateForEmployees(any(), anyList())).thenReturn(bases);
		when(payrollBaseAccumulator.accumulateDetailed(any(), anyList()))
				.thenReturn(PayrollBaseAccumulationResult.of(bases, Map.of()));
		when(employeeRepository.findByTenantIdAndCompanyIdAndIdIn(tenantId, companyId, List.of(EMPLOYEE)))
				.thenReturn(List.of(employeeWithHireDate(EMPLOYEE, tenantId, companyId, LocalDate.of(2001, 2, 1))));
		stubMonthlyCompensation(tenantId, companyId, new BigDecimal("6000.0000"));
		SurinameTaxRulesSnapshot snapshot = snapshotWithJubileeRule();

		PayrollContext payroll = new PayrollContext(tenantId, companyId, "SR", "SRD", null, UUID.randomUUID(),
				List.of(EMPLOYEE), LocalDate.of(2026, 2, 28));
		List<EvaluatedComponentAmount> result = service.applyDerivedLines(payroll, countryContext(payroll, snapshot),
				List.of(jubileeTax), List.of(jubileeLine), bases, null);

		assertThat(result).anySatisfy(line -> {
			assertThat(line.tenantWageComponentCode()).isEqualTo("1048");
			assertThat(line.evaluatedAmount()).isEqualByComparingTo("6000.0000");
		});
	}

	@Test
	void appliesLumpSumWageTaxOnPayout() throws Exception {
		UUID lumpSumTaxComponent = UUID.randomUUID();
		TenantWageComponentEntity lumpSumTax = component(lumpSumTaxComponent, "1024",
				SurinameCountryRuleKeys.WAGE_TAX_LUMP_SUM);
		EvaluatedComponentAmount lumpSumLine = EvaluatedComponentAmount.tenant(EMPLOYEE, UUID.randomUUID(), "1009",
				CalculationMethod.FIXED_AMOUNT.name(), new BigDecimal("50000.0000"), null);
		Map<UUID, Map<String, BigDecimal>> bases = Map.of(EMPLOYEE,
				Map.of(SurinameCountryRuleAlgorithms.LOONBELASTING_BASE, new BigDecimal("56000.0000")));
		when(payrollBaseAccumulator.accumulateForEmployees(any(), anyList())).thenReturn(bases);
		when(payrollBaseAccumulator.accumulateDetailed(any(), anyList()))
				.thenReturn(PayrollBaseAccumulationResult.of(bases, Map.of()));
		SurinameTaxRulesSnapshot snapshot = snapshotWithPaymentsAtOnceRule();

		PayrollContext payroll = new PayrollContext(UUID.randomUUID(), UUID.randomUUID(), "SR", "SRD", null,
				UUID.randomUUID(), List.of(EMPLOYEE), LocalDate.of(2026, 2, 28));
		List<EvaluatedComponentAmount> result = service.applyDerivedLines(payroll, countryContext(payroll, snapshot),
				List.of(lumpSumTax), List.of(lumpSumLine), bases, null);

		assertThat(result).anySatisfy(line -> {
			assertThat(line.tenantWageComponentId()).isEqualTo(lumpSumTaxComponent);
			assertThat(line.tenantWageComponentCode()).isEqualTo("1024");
			assertThat(line.evaluatedAmount()).isEqualByComparingTo("3300.0000");
		});
	}

	@Test
	void appliesAovOnVacationPayout() {
		TenantWageComponentEntity vacationAov = component(VACATION_AOV_COMPONENT, "1014",
				SurinameCountryRuleKeys.AOV_VACATION_ALLOWANCE);
		EvaluatedComponentAmount vacationLine = EvaluatedComponentAmount.tenant(EMPLOYEE, UUID.randomUUID(), "1006",
				CalculationMethod.FIXED_AMOUNT.name(), new BigDecimal("30000.0000"), null);
		Map<UUID, Map<String, BigDecimal>> bases = Map.of(EMPLOYEE,
				Map.of(SurinameCountryRuleAlgorithms.LOONBELASTING_BASE, new BigDecimal("36900.0000"),
						"AOV", new BigDecimal("37590.0000")));
		when(payrollBaseAccumulator.accumulateForEmployees(any(), anyList())).thenReturn(bases);
		when(payrollBaseAccumulator.accumulateDetailed(any(), anyList()))
				.thenReturn(PayrollBaseAccumulationResult.of(bases, Map.of()));
		SurinameTaxRulesSnapshot snapshot = snapshotWithAov();

		PayrollContext payroll = new PayrollContext(UUID.randomUUID(), UUID.randomUUID(), "SR", "SRD", null,
				UUID.randomUUID(), List.of(EMPLOYEE), LocalDate.of(2026, 2, 28));
		List<EvaluatedComponentAmount> result = service.applyDerivedLines(payroll, countryContext(payroll, snapshot),
				List.of(vacationAov), List.of(vacationLine), bases, null);

		assertThat(result).anySatisfy(line -> {
			assertThat(line.tenantWageComponentId()).isEqualTo(VACATION_AOV_COMPONENT);
			assertThat(line.tenantWageComponentCode()).isEqualTo("1014");
			assertThat(line.evaluatedAmount()).isEqualByComparingTo("1200.0000");
		});
	}

	private static TenantWageComponentEntity component(UUID id, String code, String countryRuleKey) {
		TenantWageComponentEntity c = new TenantWageComponentEntity();
		c.setId(id);
		c.setCode(code);
		c.setCalculationMethod(CalculationMethod.PERCENTAGE);
		c.setCountryRuleKey(countryRuleKey);
		return c;
	}

	private static CountryRuleContext countryContext(PayrollContext payroll, SurinameTaxRulesSnapshot snapshot) {
		CountryRuleContext ctx = new CountryRuleContext(payroll);
		ctx.putAttribute(SurinameCountryContextKeys.TAX_RULES_SNAPSHOT, snapshot);
		return ctx;
	}

	private static SurinameTaxRulesSnapshot snapshotWithJubileeRule() throws Exception {
		var rule = new ResolvedSurinameTaxRule(UUID.fromString("52000000-0000-0000-0000-000000000004"),
				"SR_SERVICE_YEARS_17A_MONTH", "Service years", LocalDate.of(2024, 1, 1), null,
				new ObjectMapper().readTree("""
						{"v":2,"freq":"MONTH","kind":"LEGACY_SERVICE_YEAR_TABLE","rows":[
						{"pct":0,"lo":0,"hi":9},
						{"pct":100,"lo":25,"hi":29},
						{"pct":300,"lo":40}]}
						"""));
		return new SurinameTaxRulesSnapshot(LocalDate.of(2026, 2, 28),
				Map.of("SR_SERVICE_YEARS_17A_MONTH", rule));
	}

	private static TenantEmployeeEntity employeeWithHireDate(UUID id, UUID tenantId, UUID companyId,
			LocalDate hireDate) {
		TenantEmployeeEntity employee = new TenantEmployeeEntity();
		employee.setId(id);
		employee.setTenantId(tenantId);
		employee.setCompanyId(companyId);
		employee.setHireDate(hireDate);
		return employee;
	}

	private void stubMonthlyCompensation(UUID tenantId, UUID companyId, BigDecimal monthWage) {
		TenantEmployeeCompensationEntity compensation = new TenantEmployeeCompensationEntity();
		compensation.setEmployeeId(EMPLOYEE);
		compensation.setTenantId(tenantId);
		compensation.setCompanyId(companyId);
		compensation.setWageType("PER_MONTH");
		compensation.setWageAmount(monthWage);
		compensation.setApplyTaxes(true);
		when(compensationRepository.findByTenantIdAndEmployeeIdIn(tenantId, List.of(EMPLOYEE)))
				.thenReturn(List.of(compensation));
		TenantCompanyEntity company = new TenantCompanyEntity();
		company.setId(companyId);
		company.setTenantId(tenantId);
		company.setPayrollFrequency("MONTHLY");
		when(companyRepository.findByIdAndTenantId(companyId, tenantId)).thenReturn(Optional.of(company));
	}

	private static SurinameTaxRulesSnapshot snapshotWithPaymentsAtOnceRule() throws Exception {
		var rule = new ResolvedSurinameTaxRule(UUID.fromString("52000000-0000-0000-0000-000000000002"),
				"SR_PAYMENTS_AT_ONCE_YEAR", "Payments at once", LocalDate.of(2024, 1, 1), null,
				new ObjectMapper().readTree("""
						{"v":2,"freq":"YEAR","kind":"MARGINAL_RATES","rows":[
						{"pct":5,"min":0,"max":42000},
						{"pct":15,"min":42000,"max":84000},
						{"pct":25,"min":84000,"max":126000},
						{"pct":35,"min":126000}]}
						"""));
		return new SurinameTaxRulesSnapshot(LocalDate.of(2026, 2, 28),
				Map.of("SR_PAYMENTS_AT_ONCE_YEAR", rule));
	}

	private static SurinameTaxRulesSnapshot snapshotWithAov() {
		var aovRule = new ResolvedSurinameTaxRule(UUID.randomUUID(), "SR_AOV_PREMIUM_MONTH", "AOV",
				LocalDate.of(2024, 1, 1), null,
				new ObjectMapper().createObjectNode().put("kind", "FLAT_RATE").put("freq", "MONTH").put("pct", 4));
		return new SurinameTaxRulesSnapshot(LocalDate.of(2026, 2, 28), Map.of("SR_AOV_PREMIUM_MONTH", aovRule));
	}
}
