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
import com.wagepayroll.domain.wagecomponent.TenantWageComponentTransactionEntity;
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
		algorithms = new SurinameCountryRuleAlgorithms();
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
		SurinameTaxRulesSnapshot snapshot = snapshotWithPaymentsAtOnceRule();

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
		SurinameTaxRulesSnapshot snapshot = snapshotWithPaymentsAtOnceRule();

		PayrollContext payroll = new PayrollContext(tenantId, companyId, "SR", "SRD", null, UUID.randomUUID(),
				List.of(EMPLOYEE), LocalDate.of(2026, 2, 28));
		List<EvaluatedComponentAmount> result = service.applyDerivedLines(payroll, countryContext(payroll, snapshot),
				List.of(jubileeTax), List.of(jubileeLine), bases, null);

		assertThat(result).anySatisfy(line -> {
			assertThat(line.tenantWageComponentCode()).isEqualTo("1048");
			assertThat(line.evaluatedAmount()).isEqualByComparingTo("300.0000");
		});
	}

	@Test
	void appliesJubileePaymentAtOnceTaxOnTwentyYearTaxableRemainder() throws Exception {
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
				.thenReturn(List.of(employeeWithHireDate(EMPLOYEE, tenantId, companyId, LocalDate.of(2006, 2, 1))));
		stubMonthlyCompensation(tenantId, companyId, new BigDecimal("6000.0000"));
		SurinameTaxRulesSnapshot snapshot = snapshotWithPaymentsAtOnceRule();

		PayrollContext payroll = new PayrollContext(tenantId, companyId, "SR", "SRD", null, UUID.randomUUID(),
				List.of(EMPLOYEE), LocalDate.of(2026, 2, 28));
		List<EvaluatedComponentAmount> result = service.applyDerivedLines(payroll, countryContext(payroll, snapshot),
				List.of(jubileeTax), List.of(jubileeLine), bases, null);

		assertThat(result).anySatisfy(line -> {
			assertThat(line.tenantWageComponentCode()).isEqualTo("1048");
			assertThat(line.evaluatedAmount()).isEqualByComparingTo("375.0000");
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

	@Test
	void appliesCompanyCarBenefitFromListPriceAcP2_1() throws Exception {
		UUID carComponentId = UUID.randomUUID();
		TenantWageComponentEntity carBenefit = componentWithDefaultAmount(carComponentId, "1049",
				SurinameCountryRuleKeys.COMPANY_CAR_BENEFIT, new BigDecimal("180000.0000"));
		Map<UUID, Map<String, BigDecimal>> bases = Map.of(EMPLOYEE,
				Map.of(SurinameCountryRuleAlgorithms.LOONBELASTING_BASE, new BigDecimal("8000.0000"),
						SurinameCountryRuleAlgorithms.GROSS_BASE, new BigDecimal("8000.0000")));
		when(payrollBaseAccumulator.accumulateForEmployees(any(), anyList())).thenReturn(bases);
		when(payrollBaseAccumulator.accumulateDetailed(any(), anyList()))
				.thenReturn(PayrollBaseAccumulationResult.of(bases, Map.of()));
		SurinameTaxRulesSnapshot snapshot = snapshotWithP2BenefitRules();

		PayrollContext payroll = new PayrollContext(UUID.randomUUID(), UUID.randomUUID(), "SR", "SRD", null,
				UUID.randomUUID(), List.of(EMPLOYEE), LocalDate.of(2026, 2, 28));
		List<EvaluatedComponentAmount> result = service.applyDerivedLines(payroll, countryContext(payroll, snapshot),
				List.of(carBenefit), List.of(), bases, null);

		assertThat(result).anySatisfy(line -> {
			assertThat(line.tenantWageComponentCode()).isEqualTo("1049");
			assertThat(line.evaluatedAmount()).isEqualByComparingTo("300.0000");
		});
	}

	@Test
	void appliesFreeHousingBenefitFromMoneyWageAcP2_2() throws Exception {
		UUID housingComponentId = UUID.randomUUID();
		TenantWageComponentEntity housingBenefit = component(housingComponentId, "1050",
				SurinameCountryRuleKeys.FREE_HOUSING_BENEFIT);
		Map<UUID, Map<String, BigDecimal>> bases = Map.of(EMPLOYEE,
				Map.of(SurinameCountryRuleAlgorithms.LOONBELASTING_BASE, new BigDecimal("8000.0000"),
						SurinameCountryRuleAlgorithms.GROSS_BASE, new BigDecimal("8000.0000")));
		when(payrollBaseAccumulator.accumulateForEmployees(any(), anyList())).thenReturn(bases);
		when(payrollBaseAccumulator.accumulateDetailed(any(), anyList()))
				.thenReturn(PayrollBaseAccumulationResult.of(bases, Map.of()));
		SurinameTaxRulesSnapshot snapshot = snapshotWithP2BenefitRules();

		PayrollContext payroll = new PayrollContext(UUID.randomUUID(), UUID.randomUUID(), "SR", "SRD", null,
				UUID.randomUUID(), List.of(EMPLOYEE), LocalDate.of(2026, 2, 28));
		List<EvaluatedComponentAmount> result = service.applyDerivedLines(payroll, countryContext(payroll, snapshot),
				List.of(housingBenefit), List.of(), bases, null);

		assertThat(result).anySatisfy(line -> {
			assertThat(line.tenantWageComponentCode()).isEqualTo("1050");
			assertThat(line.evaluatedAmount()).isEqualByComparingTo("600.0000");
		});
	}

	@Test
	void appliesBoardLodgingBenefitFromQuantityAcP2_3() throws Exception {
		assertQuantityBenefit("1051", SurinameCountryRuleKeys.BOARD_LODGING_BENEFIT, new BigDecimal("15"),
				"150.0000");
	}

	@Test
	void appliesBoardBenefitFromQuantityAcP2_3b() throws Exception {
		assertQuantityBenefit("1052", SurinameCountryRuleKeys.BOARD_BENEFIT, new BigDecimal("20"), "100.0000");
	}

	@Test
	void appliesHotMealBenefitFromQuantityAcP2_4() throws Exception {
		assertQuantityBenefit("1053", SurinameCountryRuleKeys.HOT_MEAL_BENEFIT, new BigDecimal("22"), "110.0000");
	}

	@Test
	void appliesBreadMealBenefitFromQuantityAcP2_5() throws Exception {
		assertQuantityBenefit("1054", SurinameCountryRuleKeys.BREAD_MEAL_BENEFIT, new BigDecimal("20"), "30.0000");
	}

	@Test
	void appliesFreeUtilitiesBenefitFromAmountAcP2_7() throws Exception {
		UUID componentId = UUID.randomUUID();
		UUID payPeriodId = UUID.randomUUID();
		UUID tenantId = UUID.randomUUID();
		UUID companyId = UUID.randomUUID();
		TenantWageComponentEntity benefit = component(componentId, "1057",
				SurinameCountryRuleKeys.FREE_UTILITIES_BENEFIT);
		TenantWageComponentTransactionEntity txn = new TenantWageComponentTransactionEntity();
		txn.setEmployeeId(EMPLOYEE);
		txn.setTenantWageComponentId(componentId);
		txn.setAmount(new BigDecimal("275.50"));
		Map<UUID, Map<String, BigDecimal>> bases = Map.of(EMPLOYEE,
				Map.of(SurinameCountryRuleAlgorithms.LOONBELASTING_BASE, new BigDecimal("8000.0000"),
						SurinameCountryRuleAlgorithms.GROSS_BASE, new BigDecimal("8000.0000")));
		when(payrollBaseAccumulator.accumulateForEmployees(any(), anyList())).thenReturn(bases);
		when(payrollBaseAccumulator.accumulateDetailed(any(), anyList()))
				.thenReturn(PayrollBaseAccumulationResult.of(bases, Map.of()));
		when(transactionRepository.findByTenantIdAndCompanyIdAndPayPeriodIdAndEmployeeIdIn(tenantId, companyId,
				payPeriodId, List.of(EMPLOYEE))).thenReturn(List.of(txn));

		PayrollContext payroll = new PayrollContext(tenantId, companyId, "SR", "SRD", null, payPeriodId,
				List.of(EMPLOYEE), LocalDate.of(2026, 2, 28));
		List<EvaluatedComponentAmount> result = service.applyDerivedLines(payroll,
				countryContext(payroll, new SurinameTaxRulesSnapshot(LocalDate.of(2026, 2, 28), Map.of())),
				List.of(benefit), List.of(), bases, null);

		assertThat(result).anySatisfy(line -> {
			assertThat(line.tenantWageComponentCode()).isEqualTo("1057");
			assertThat(line.evaluatedAmount()).isEqualByComparingTo("275.5000");
		});
	}

	@Test
	void appliesExchangeRateCompensationAndExclusionAcP2_6() throws Exception {
		UUID exchangeComponentId = UUID.randomUUID();
		UUID exchangeTaxComponentId = UUID.randomUUID();
		UUID payPeriodId = UUID.randomUUID();
		UUID tenantId = UUID.randomUUID();
		UUID companyId = UUID.randomUUID();
		TenantWageComponentEntity exchangePayout = component(exchangeComponentId, "1055",
				SurinameCountryRuleKeys.EXCHANGE_RATE_COMPENSATION);
		TenantWageComponentEntity exchangeExclusion = component(exchangeTaxComponentId, "1056",
				SurinameCountryRuleKeys.WAGE_TAX_EXCHANGE_RATE);
		TenantWageComponentTransactionEntity txn = new TenantWageComponentTransactionEntity();
		txn.setEmployeeId(EMPLOYEE);
		txn.setTenantWageComponentId(exchangeComponentId);
		txn.setAmount(new BigDecimal("950.0000"));
		Map<UUID, Map<String, BigDecimal>> bases = Map.of(EMPLOYEE,
				Map.of(SurinameCountryRuleAlgorithms.LOONBELASTING_BASE, new BigDecimal("8000.0000"),
						SurinameCountryRuleAlgorithms.GROSS_BASE, new BigDecimal("8000.0000")));
		when(payrollBaseAccumulator.accumulateForEmployees(any(), anyList())).thenReturn(bases);
		when(payrollBaseAccumulator.accumulateDetailed(any(), anyList()))
				.thenReturn(PayrollBaseAccumulationResult.of(bases, Map.of()));
		when(transactionRepository.findByTenantIdAndCompanyIdAndPayPeriodIdAndEmployeeIdIn(tenantId, companyId,
				payPeriodId, List.of(EMPLOYEE))).thenReturn(List.of(txn));
		SurinameTaxRulesSnapshot snapshot = snapshotWithExchangeRateRule();

		PayrollContext payroll = new PayrollContext(tenantId, companyId, "SR", "SRD", null, payPeriodId,
				List.of(EMPLOYEE), LocalDate.of(2026, 2, 28));
		List<EvaluatedComponentAmount> result = service.applyDerivedLines(payroll, countryContext(payroll, snapshot),
				List.of(exchangePayout, exchangeExclusion), List.of(), bases, null);

		assertThat(result).anySatisfy(line -> {
			assertThat(line.tenantWageComponentCode()).isEqualTo("1055");
			assertThat(line.evaluatedAmount()).isEqualByComparingTo("950.0000");
		});
		assertThat(result).anySatisfy(line -> {
			assertThat(line.tenantWageComponentCode()).isEqualTo("1056");
			assertThat(line.evaluatedAmount()).isEqualByComparingTo("800.0000");
		});
	}

	@Test
	void appliesTrainingPayoutAndFullExclusionAcP4_3() throws Exception {
		UUID trainingComponentId = UUID.randomUUID();
		UUID trainingTaxComponentId = UUID.randomUUID();
		UUID payPeriodId = UUID.randomUUID();
		UUID tenantId = UUID.randomUUID();
		UUID companyId = UUID.randomUUID();
		TenantWageComponentEntity trainingPayout = component(trainingComponentId, "1062",
				SurinameCountryRuleKeys.TRAINING_PAYOUT);
		TenantWageComponentEntity trainingExclusion = component(trainingTaxComponentId, "1063",
				SurinameCountryRuleKeys.WAGE_TAX_TRAINING);
		TenantWageComponentTransactionEntity txn = new TenantWageComponentTransactionEntity();
		txn.setEmployeeId(EMPLOYEE);
		txn.setTenantWageComponentId(trainingComponentId);
		txn.setAmount(new BigDecimal("3500.0000"));
		Map<UUID, Map<String, BigDecimal>> bases = Map.of(EMPLOYEE,
				Map.of(SurinameCountryRuleAlgorithms.LOONBELASTING_BASE, new BigDecimal("8000.0000"),
						SurinameCountryRuleAlgorithms.GROSS_BASE, new BigDecimal("8000.0000")));
		when(payrollBaseAccumulator.accumulateForEmployees(any(), anyList())).thenReturn(bases);
		when(payrollBaseAccumulator.accumulateDetailed(any(), anyList()))
				.thenReturn(PayrollBaseAccumulationResult.of(bases, Map.of()));
		when(transactionRepository.findByTenantIdAndCompanyIdAndPayPeriodIdAndEmployeeIdIn(tenantId, companyId,
				payPeriodId, List.of(EMPLOYEE))).thenReturn(List.of(txn));
		SurinameTaxRulesSnapshot snapshot = snapshotWithAovBeneficiaryRule();

		PayrollContext payroll = new PayrollContext(tenantId, companyId, "SR", "SRD", null, payPeriodId,
				List.of(EMPLOYEE), LocalDate.of(2026, 2, 28));
		List<EvaluatedComponentAmount> result = service.applyDerivedLines(payroll, countryContext(payroll, snapshot),
				List.of(trainingPayout, trainingExclusion), List.of(), bases, null);

		assertThat(result).anySatisfy(line -> {
			assertThat(line.tenantWageComponentCode()).isEqualTo("1062");
			assertThat(line.evaluatedAmount()).isEqualByComparingTo("3500.0000");
		});
		assertThat(result).anySatisfy(line -> {
			assertThat(line.tenantWageComponentCode()).isEqualTo("1063");
			assertThat(line.evaluatedAmount()).isEqualByComparingTo("3500.0000");
		});
	}

	@Test
	void appliesCommuteTransportPayoutAndFullExclusionAcP4_2() throws Exception {
		UUID transportComponentId = UUID.randomUUID();
		UUID transportTaxComponentId = UUID.randomUUID();
		UUID payPeriodId = UUID.randomUUID();
		UUID tenantId = UUID.randomUUID();
		UUID companyId = UUID.randomUUID();
		TenantWageComponentEntity transportPayout = component(transportComponentId, "1060",
				SurinameCountryRuleKeys.COMMUTE_TRANSPORT_PAYOUT);
		TenantWageComponentEntity transportExclusion = component(transportTaxComponentId, "1061",
				SurinameCountryRuleKeys.WAGE_TAX_COMMUTE_TRANSPORT);
		TenantWageComponentTransactionEntity txn = new TenantWageComponentTransactionEntity();
		txn.setEmployeeId(EMPLOYEE);
		txn.setTenantWageComponentId(transportComponentId);
		txn.setAmount(new BigDecimal("1200.0000"));
		Map<UUID, Map<String, BigDecimal>> bases = Map.of(EMPLOYEE,
				Map.of(SurinameCountryRuleAlgorithms.LOONBELASTING_BASE, new BigDecimal("8000.0000"),
						SurinameCountryRuleAlgorithms.GROSS_BASE, new BigDecimal("8000.0000")));
		when(payrollBaseAccumulator.accumulateForEmployees(any(), anyList())).thenReturn(bases);
		when(payrollBaseAccumulator.accumulateDetailed(any(), anyList()))
				.thenReturn(PayrollBaseAccumulationResult.of(bases, Map.of()));
		when(transactionRepository.findByTenantIdAndCompanyIdAndPayPeriodIdAndEmployeeIdIn(tenantId, companyId,
				payPeriodId, List.of(EMPLOYEE))).thenReturn(List.of(txn));
		SurinameTaxRulesSnapshot snapshot = snapshotWithAovBeneficiaryRule();

		PayrollContext payroll = new PayrollContext(tenantId, companyId, "SR", "SRD", null, payPeriodId,
				List.of(EMPLOYEE), LocalDate.of(2026, 2, 28));
		List<EvaluatedComponentAmount> result = service.applyDerivedLines(payroll, countryContext(payroll, snapshot),
				List.of(transportPayout, transportExclusion), List.of(), bases, null);

		assertThat(result).anySatisfy(line -> {
			assertThat(line.tenantWageComponentCode()).isEqualTo("1060");
			assertThat(line.evaluatedAmount()).isEqualByComparingTo("1200.0000");
		});
		assertThat(result).anySatisfy(line -> {
			assertThat(line.tenantWageComponentCode()).isEqualTo("1061");
			assertThat(line.evaluatedAmount()).isEqualByComparingTo("1200.0000");
		});
	}

	@Test
	void appliesCostAllowancePayoutAndFullExclusionAcP4_1() throws Exception {
		UUID costComponentId = UUID.randomUUID();
		UUID costTaxComponentId = UUID.randomUUID();
		UUID payPeriodId = UUID.randomUUID();
		UUID tenantId = UUID.randomUUID();
		UUID companyId = UUID.randomUUID();
		TenantWageComponentEntity costPayout = component(costComponentId, "1058",
				SurinameCountryRuleKeys.COST_ALLOWANCE_PAYOUT);
		TenantWageComponentEntity costExclusion = component(costTaxComponentId, "1059",
				SurinameCountryRuleKeys.WAGE_TAX_COST_ALLOWANCE);
		TenantWageComponentTransactionEntity txn = new TenantWageComponentTransactionEntity();
		txn.setEmployeeId(EMPLOYEE);
		txn.setTenantWageComponentId(costComponentId);
		txn.setAmount(new BigDecimal("425.0000"));
		Map<UUID, Map<String, BigDecimal>> bases = Map.of(EMPLOYEE,
				Map.of(SurinameCountryRuleAlgorithms.LOONBELASTING_BASE, new BigDecimal("8000.0000"),
						SurinameCountryRuleAlgorithms.GROSS_BASE, new BigDecimal("8000.0000")));
		when(payrollBaseAccumulator.accumulateForEmployees(any(), anyList())).thenReturn(bases);
		when(payrollBaseAccumulator.accumulateDetailed(any(), anyList()))
				.thenReturn(PayrollBaseAccumulationResult.of(bases, Map.of()));
		when(transactionRepository.findByTenantIdAndCompanyIdAndPayPeriodIdAndEmployeeIdIn(tenantId, companyId,
				payPeriodId, List.of(EMPLOYEE))).thenReturn(List.of(txn));
		SurinameTaxRulesSnapshot snapshot = snapshotWithAovBeneficiaryRule();

		PayrollContext payroll = new PayrollContext(tenantId, companyId, "SR", "SRD", null, payPeriodId,
				List.of(EMPLOYEE), LocalDate.of(2026, 2, 28));
		List<EvaluatedComponentAmount> result = service.applyDerivedLines(payroll, countryContext(payroll, snapshot),
				List.of(costPayout, costExclusion), List.of(), bases, null);

		assertThat(result).anySatisfy(line -> {
			assertThat(line.tenantWageComponentCode()).isEqualTo("1058");
			assertThat(line.evaluatedAmount()).isEqualByComparingTo("425.0000");
		});
		assertThat(result).anySatisfy(line -> {
			assertThat(line.tenantWageComponentCode()).isEqualTo("1059");
			assertThat(line.evaluatedAmount()).isEqualByComparingTo("425.0000");
		});
	}

	@Test
	void appliesPensionSchemePayoutAnd2xAovExclusionAcP4_4() throws Exception {
		UUID pensionComponentId = UUID.randomUUID();
		UUID pensionTaxComponentId = UUID.randomUUID();
		UUID payPeriodId = UUID.randomUUID();
		UUID tenantId = UUID.randomUUID();
		UUID companyId = UUID.randomUUID();
		TenantWageComponentEntity pensionPayout = component(pensionComponentId, "1064",
				SurinameCountryRuleKeys.PENSION_SCHEME_PAYOUT);
		TenantWageComponentEntity pensionExclusion = component(pensionTaxComponentId, "1065",
				SurinameCountryRuleKeys.WAGE_TAX_PENSION_2X_AOV);
		TenantWageComponentTransactionEntity txn = new TenantWageComponentTransactionEntity();
		txn.setEmployeeId(EMPLOYEE);
		txn.setTenantWageComponentId(pensionComponentId);
		txn.setAmount(new BigDecimal("5000.0000"));
		Map<UUID, Map<String, BigDecimal>> bases = Map.of(EMPLOYEE,
				Map.of(SurinameCountryRuleAlgorithms.LOONBELASTING_BASE, new BigDecimal("8000.0000"),
						SurinameCountryRuleAlgorithms.GROSS_BASE, new BigDecimal("8000.0000")));
		when(payrollBaseAccumulator.accumulateForEmployees(any(), anyList())).thenReturn(bases);
		when(payrollBaseAccumulator.accumulateDetailed(any(), anyList()))
				.thenReturn(PayrollBaseAccumulationResult.of(bases, Map.of()));
		when(transactionRepository.findByTenantIdAndCompanyIdAndPayPeriodIdAndEmployeeIdIn(tenantId, companyId,
				payPeriodId, List.of(EMPLOYEE))).thenReturn(List.of(txn));
		SurinameTaxRulesSnapshot snapshot = snapshotWithAovBeneficiaryRule();

		PayrollContext payroll = new PayrollContext(tenantId, companyId, "SR", "SRD", null, payPeriodId,
				List.of(EMPLOYEE), LocalDate.of(2026, 2, 28));
		List<EvaluatedComponentAmount> result = service.applyDerivedLines(payroll, countryContext(payroll, snapshot),
				List.of(pensionPayout, pensionExclusion), List.of(), bases, null);

		assertThat(result).anySatisfy(line -> {
			assertThat(line.tenantWageComponentCode()).isEqualTo("1064");
			assertThat(line.evaluatedAmount()).isEqualByComparingTo("5000.0000");
		});
		assertThat(result).anySatisfy(line -> {
			assertThat(line.tenantWageComponentCode()).isEqualTo("1065");
			assertThat(line.evaluatedAmount()).isEqualByComparingTo("4500.0000");
		});
	}

	@Test
	void appliesPension2xAovFullExclusionAcP4_4b() throws Exception {
		UUID pensionComponentId = UUID.randomUUID();
		UUID pensionTaxComponentId = UUID.randomUUID();
		UUID payPeriodId = UUID.randomUUID();
		UUID tenantId = UUID.randomUUID();
		UUID companyId = UUID.randomUUID();
		TenantWageComponentEntity pensionPayout = component(pensionComponentId, "1064",
				SurinameCountryRuleKeys.PENSION_SCHEME_PAYOUT);
		TenantWageComponentEntity pensionExclusion = component(pensionTaxComponentId, "1065",
				SurinameCountryRuleKeys.WAGE_TAX_PENSION_2X_AOV);
		TenantWageComponentTransactionEntity txn = new TenantWageComponentTransactionEntity();
		txn.setEmployeeId(EMPLOYEE);
		txn.setTenantWageComponentId(pensionComponentId);
		txn.setAmount(new BigDecimal("3000.0000"));
		Map<UUID, Map<String, BigDecimal>> bases = Map.of(EMPLOYEE,
				Map.of(SurinameCountryRuleAlgorithms.LOONBELASTING_BASE, new BigDecimal("8000.0000"),
						SurinameCountryRuleAlgorithms.GROSS_BASE, new BigDecimal("8000.0000")));
		when(payrollBaseAccumulator.accumulateForEmployees(any(), anyList())).thenReturn(bases);
		when(payrollBaseAccumulator.accumulateDetailed(any(), anyList()))
				.thenReturn(PayrollBaseAccumulationResult.of(bases, Map.of()));
		when(transactionRepository.findByTenantIdAndCompanyIdAndPayPeriodIdAndEmployeeIdIn(tenantId, companyId,
				payPeriodId, List.of(EMPLOYEE))).thenReturn(List.of(txn));
		SurinameTaxRulesSnapshot snapshot = snapshotWithAovBeneficiaryRule();

		PayrollContext payroll = new PayrollContext(tenantId, companyId, "SR", "SRD", null, payPeriodId,
				List.of(EMPLOYEE), LocalDate.of(2026, 2, 28));
		List<EvaluatedComponentAmount> result = service.applyDerivedLines(payroll, countryContext(payroll, snapshot),
				List.of(pensionPayout, pensionExclusion), List.of(), bases, null);

		assertThat(result).anySatisfy(line -> {
			assertThat(line.tenantWageComponentCode()).isEqualTo("1065");
			assertThat(line.evaluatedAmount()).isEqualByComparingTo("3000.0000");
		});
	}

	private void assertQuantityBenefit(String templateCode, String countryRuleKey, BigDecimal quantity,
			String expectedAmount) throws Exception {
		UUID componentId = UUID.randomUUID();
		UUID payPeriodId = UUID.randomUUID();
		UUID tenantId = UUID.randomUUID();
		UUID companyId = UUID.randomUUID();
		TenantWageComponentEntity benefit = component(componentId, templateCode, countryRuleKey);
		TenantWageComponentTransactionEntity txn = new TenantWageComponentTransactionEntity();
		txn.setEmployeeId(EMPLOYEE);
		txn.setTenantWageComponentId(componentId);
		txn.setQuantity(quantity);
		Map<UUID, Map<String, BigDecimal>> bases = Map.of(EMPLOYEE,
				Map.of(SurinameCountryRuleAlgorithms.LOONBELASTING_BASE, new BigDecimal("8000.0000"),
						SurinameCountryRuleAlgorithms.GROSS_BASE, new BigDecimal("8000.0000")));
		when(payrollBaseAccumulator.accumulateForEmployees(any(), anyList())).thenReturn(bases);
		when(payrollBaseAccumulator.accumulateDetailed(any(), anyList()))
				.thenReturn(PayrollBaseAccumulationResult.of(bases, Map.of()));
		when(transactionRepository.findByTenantIdAndCompanyIdAndPayPeriodIdAndEmployeeIdIn(tenantId, companyId,
				payPeriodId, List.of(EMPLOYEE))).thenReturn(List.of(txn));
		SurinameTaxRulesSnapshot snapshot = snapshotWithBoardMealRules();

		PayrollContext payroll = new PayrollContext(tenantId, companyId, "SR", "SRD", null, payPeriodId,
				List.of(EMPLOYEE), LocalDate.of(2026, 2, 28));
		List<EvaluatedComponentAmount> result = service.applyDerivedLines(payroll, countryContext(payroll, snapshot),
				List.of(benefit), List.of(), bases, null);

		assertThat(result).anySatisfy(line -> {
			assertThat(line.tenantWageComponentCode()).isEqualTo(templateCode);
			assertThat(line.evaluatedAmount()).isEqualByComparingTo(expectedAmount);
		});
	}

	private static SurinameTaxRulesSnapshot snapshotWithBoardMealRules() throws Exception {
		ObjectMapper mapper = new ObjectMapper();
		var boardLodging = new ResolvedSurinameTaxRule(UUID.fromString("52000000-0000-0000-0000-000000000013"),
				SurinameCountryRuleKeys.RULE_BOARD_LODGING_DAY, "Board lodging", LocalDate.of(2024, 1, 1), null,
				mapper.readTree("{\"v\":2,\"freq\":\"MONTH\",\"kind\":\"UNIT_CAP\",\"amount\":10}"));
		var board = new ResolvedSurinameTaxRule(UUID.fromString("52000000-0000-0000-0000-000000000014"),
				SurinameCountryRuleKeys.RULE_BOARD_DAY, "Board", LocalDate.of(2024, 1, 1), null,
				mapper.readTree("{\"v\":2,\"freq\":\"MONTH\",\"kind\":\"UNIT_CAP\",\"amount\":5}"));
		var hotMeal = new ResolvedSurinameTaxRule(UUID.fromString("52000000-0000-0000-0000-000000000015"),
				SurinameCountryRuleKeys.RULE_HOT_MEAL_UNIT, "Hot meal", LocalDate.of(2024, 1, 1), null,
				mapper.readTree("{\"v\":2,\"freq\":\"MONTH\",\"kind\":\"UNIT_CAP\",\"amount\":5}"));
		var breadMeal = new ResolvedSurinameTaxRule(UUID.fromString("52000000-0000-0000-0000-000000000016"),
				SurinameCountryRuleKeys.RULE_BREAD_MEAL_UNIT, "Bread meal", LocalDate.of(2024, 1, 1), null,
				mapper.readTree("{\"v\":2,\"freq\":\"MONTH\",\"kind\":\"UNIT_CAP\",\"amount\":1.5}"));
		return new SurinameTaxRulesSnapshot(LocalDate.of(2026, 2, 28),
				Map.of(SurinameCountryRuleKeys.RULE_BOARD_LODGING_DAY, boardLodging,
						SurinameCountryRuleKeys.RULE_BOARD_DAY, board, SurinameCountryRuleKeys.RULE_HOT_MEAL_UNIT,
						hotMeal, SurinameCountryRuleKeys.RULE_BREAD_MEAL_UNIT, breadMeal));
	}

	private static SurinameTaxRulesSnapshot snapshotWithExchangeRateRule() throws Exception {
		var rule = new ResolvedSurinameTaxRule(UUID.fromString("52000000-0000-0000-0000-000000000012"),
				SurinameCountryRuleKeys.RULE_EXCHANGE_RATE_COMPENSATION_MONTH, "Exchange rate", LocalDate.of(2022, 1, 1),
				null, new ObjectMapper().readTree("{\"v\":2,\"freq\":\"MONTH\",\"kind\":\"THRESHOLD_AMOUNT\",\"amount\":800}"));
		return new SurinameTaxRulesSnapshot(LocalDate.of(2026, 2, 28),
				Map.of(SurinameCountryRuleKeys.RULE_EXCHANGE_RATE_COMPENSATION_MONTH, rule));
	}

	private static SurinameTaxRulesSnapshot snapshotWithAovBeneficiaryRule() throws Exception {
		var rule = new ResolvedSurinameTaxRule(UUID.fromString("52000000-0000-0000-0000-000000000014"),
				SurinameCountryRuleKeys.RULE_AOV_BENEFICIARY_MONTH, "AOV beneficiary", LocalDate.of(2025, 3, 1), null,
				new ObjectMapper().readTree("{\"v\":2,\"freq\":\"MONTH\",\"kind\":\"THRESHOLD_AMOUNT\",\"amount\":2250}"));
		return new SurinameTaxRulesSnapshot(LocalDate.of(2026, 2, 28),
				Map.of(SurinameCountryRuleKeys.RULE_AOV_BENEFICIARY_MONTH, rule));
	}

	private static TenantWageComponentEntity componentWithDefaultAmount(UUID id, String code, String countryRuleKey,
			BigDecimal defaultAmount) {
		TenantWageComponentEntity c = component(id, code, countryRuleKey);
		c.setDefaultAmount(defaultAmount);
		return c;
	}

	private static SurinameTaxRulesSnapshot snapshotWithP2BenefitRules() throws Exception {
		var carRule = new ResolvedSurinameTaxRule(UUID.fromString("52000000-0000-0000-0000-00000000000e"),
				SurinameCountryRuleKeys.RULE_COMPANY_CAR_YEAR, "Company car", LocalDate.of(2024, 1, 1), null,
				new ObjectMapper().readTree("{\"v\":2,\"freq\":\"YEAR\",\"kind\":\"FLAT_RATE\",\"pct\":2}"));
		var housingRule = new ResolvedSurinameTaxRule(UUID.fromString("52000000-0000-0000-0000-00000000000f"),
				SurinameCountryRuleKeys.RULE_FREE_HOUSING_YEAR, "Free housing", LocalDate.of(2024, 1, 1), null,
				new ObjectMapper().readTree("{\"v\":2,\"freq\":\"YEAR\",\"kind\":\"FLAT_RATE\",\"pct\":7.5}"));
		return new SurinameTaxRulesSnapshot(LocalDate.of(2026, 2, 28),
				Map.of(SurinameCountryRuleKeys.RULE_COMPANY_CAR_YEAR, carRule,
						SurinameCountryRuleKeys.RULE_FREE_HOUSING_YEAR, housingRule));
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
