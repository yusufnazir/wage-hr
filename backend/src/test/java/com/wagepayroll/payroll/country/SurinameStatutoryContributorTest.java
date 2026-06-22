package com.wagepayroll.payroll.country;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
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
import com.wagepayroll.domain.org.TenantCompanyRepository;
import com.wagepayroll.domain.wagecomponent.PlatformWageComponentEntity;
import com.wagepayroll.domain.wagecomponent.PlatformWageComponentRepository;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentTransactionEntity;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentTransactionRepository;
import com.wagepayroll.payroll.engine.EvaluatedComponentAmount;
import com.wagepayroll.payroll.engine.PayrollContext;
import com.wagepayroll.payroll.engine.PayrollRunState;
import com.wagepayroll.payroll.model.CalculationMethod;

@ExtendWith(MockitoExtension.class)
class SurinameStatutoryContributorTest {

	private static final UUID TENANT = UUID.randomUUID();
	private static final UUID COMPANY = UUID.randomUUID();
	private static final UUID PAY_PERIOD = UUID.randomUUID();
	private static final UUID EMPLOYEE = UUID.randomUUID();
	private static final UUID WAGE_TAX_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
	private static final UUID AOV_ID = UUID.fromString("50000000-0000-0000-0000-000000000002");

	@Mock
	private PlatformWageComponentRepository platformWageComponentRepository;

	@Mock
	private TenantEmployeeCompensationRepository compensationRepository;

	@Mock
	private TenantCompanyRepository companyRepository;

	@Mock
	private TenantWageComponentTransactionRepository transactionRepository;

	private final List<TenantWageComponentTransactionEntity> periodTransactions = new ArrayList<>();

	private SurinameStatutoryContributor contributor;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@BeforeEach
	void setUp() {
		periodTransactions.clear();
		contributor = new SurinameStatutoryContributor(new SurinameWageTaxCalculator(),
				new SurinameCountryRuleAlgorithms(), platformWageComponentRepository, compensationRepository,
				companyRepository, transactionRepository);
		lenient().when(transactionRepository.findByTenantIdAndCompanyIdAndPayPeriodIdAndEmployeeIdIn(eq(TENANT), eq(COMPANY),
				eq(PAY_PERIOD), eq(List.of(EMPLOYEE)))).thenAnswer(invocation -> List.copyOf(periodTransactions));
	}

	@Test
	void wageTaxMatchesNormalMonthlyExampleWithBelastingvrijAndBeroepskosten() throws Exception {
		stubPlatformComponents();
		TenantEmployeeCompensationEntity compensation = new TenantEmployeeCompensationEntity();
		compensation.setEmployeeId(EMPLOYEE);
		compensation.setApplyTaxes(true);
		compensation.setApplyAov(false);
		compensation.setApplyTaxExempt(true);
		when(compensationRepository.findByTenantIdAndEmployeeIdIn(TENANT, List.of(EMPLOYEE)))
				.thenReturn(List.of(compensation));

		PayrollRunState state = stateWithBases(new BigDecimal("15000.0000"), new BigDecimal("15000.0000"));
		contributor.contribute(state);

		assertThat(state.statutoryEvaluatedAmounts()).anySatisfy(line -> {
			assertThat(line.tenantWageComponentCode()).isEqualTo("WAGE_TAX");
			assertThat(line.evaluatedAmount()).isEqualByComparingTo("658.0000");
		});
	}

	@Test
	void skipsWageTaxWhenEmployeeOptedOut() throws Exception {
		stubPlatformComponents();
		stubCompensation(false, true);
		PayrollRunState state = stateWithBases(new BigDecimal("18500.0000"));

		contributor.contribute(state);

		assertThat(state.statutoryEvaluatedAmounts()).noneMatch(line -> "WAGE_TAX".equals(line.tenantWageComponentCode()));
		assertThat(state.statutoryEvaluatedAmounts()).anyMatch(line -> "SOCIAL_PREMIUM_EE".equals(line.tenantWageComponentCode()));
	}

	@Test
	void skipsAovWhenEmployeeOptedOut() throws Exception {
		stubPlatformComponents();
		stubCompensation(true, false);
		PayrollRunState state = stateWithBases(new BigDecimal("18500.0000"));

		contributor.contribute(state);

		assertThat(state.statutoryEvaluatedAmounts()).anyMatch(line -> "WAGE_TAX".equals(line.tenantWageComponentCode()));
		assertThat(state.statutoryEvaluatedAmounts()).noneMatch(line -> "SOCIAL_PREMIUM_EE".equals(line.tenantWageComponentCode()));
	}

	@Test
	void wageTaxReducedByCostAllowancePayoutExclusion() throws Exception {
		stubPlatformComponents();
		stubCompensation(true, false, false);
		PayrollRunState without = stateWithBases(new BigDecimal("10000.0000"));
		contributor.contribute(without);
		BigDecimal taxWithout = wageTaxAmount(without);

		PayrollRunState with = stateWithBases(new BigDecimal("10000.0000"));
		stubPeriodPayout(with, "1058", new BigDecimal("425.0000"));
		contributor.contribute(with);
		BigDecimal taxWith = wageTaxAmount(with);

		assertThat(taxWith).isLessThan(taxWithout);
		assertThat(taxWithout.subtract(taxWith)).isGreaterThan(BigDecimal.ZERO);
	}

	@Test
	void eachAdditionalP4ExclusionFurtherReducesWageTax() throws Exception {
		stubPlatformComponents();
		stubCompensation(true, false, false);
		PayrollRunState baseline = stateWithBases(new BigDecimal("10000.0000"));
		contributor.contribute(baseline);
		BigDecimal taxBaseline = wageTaxAmount(baseline);

		periodTransactions.clear();
		PayrollRunState withCost = stateWithBases(new BigDecimal("10000.0000"));
		stubPeriodPayout(withCost, "1058", new BigDecimal("425.0000"));
		contributor.contribute(withCost);
		BigDecimal taxCost = wageTaxAmount(withCost);

		periodTransactions.clear();
		PayrollRunState withCostAndTraining = stateWithBases(new BigDecimal("10000.0000"));
		stubPeriodPayout(withCostAndTraining, "1058", new BigDecimal("425.0000"));
		stubPeriodPayout(withCostAndTraining, "1062", new BigDecimal("3500.0000"));
		contributor.contribute(withCostAndTraining);
		BigDecimal taxBoth = wageTaxAmount(withCostAndTraining);

		assertThat(taxCost).isLessThan(taxBaseline);
		assertThat(taxBoth).isLessThan(taxCost);
	}

	@Test
	void pensionPartialExclusionReducesWageTaxMoreThanSmallerPayout() throws Exception {
		stubPlatformComponents();
		stubCompensation(true, false, false);
		periodTransactions.clear();
		PayrollRunState underCap = stateWithBases(Map.of("LOONBELASTING", new BigDecimal("10000.0000"), "GROSS",
				new BigDecimal("10000.0000"), "AOV", new BigDecimal("10000.0000")), snapshotWithPensionCapRule());
		stubPeriodPayout(underCap, "1064", new BigDecimal("3000.0000"));
		contributor.contribute(underCap);

		periodTransactions.clear();
		PayrollRunState overCap = stateWithBases(Map.of("LOONBELASTING", new BigDecimal("10000.0000"), "GROSS",
				new BigDecimal("10000.0000"), "AOV", new BigDecimal("10000.0000")), snapshotWithPensionCapRule());
		stubPeriodPayout(overCap, "1064", new BigDecimal("5000.0000"));
		contributor.contribute(overCap);

		assertThat(wageTaxAmount(overCap)).isLessThan(wageTaxAmount(underCap));
	}

	@Test
	void aovSubtractsVacationPayoutFromLabelBase() throws Exception {
		stubPlatformComponents();
		stubCompensation(false, true);
		PayrollRunState state = stateWithBases(new BigDecimal("10000.0000"));
		state.evaluatedComponentAmounts().add(EvaluatedComponentAmount.tenant(EMPLOYEE, UUID.randomUUID(), "1006",
				CalculationMethod.FIXED_AMOUNT.name(), new BigDecimal("500.0000"), null));

		contributor.contribute(state);

		assertThat(state.statutoryEvaluatedAmounts()).anySatisfy(line -> {
			assertThat(line.tenantWageComponentCode()).isEqualTo("SOCIAL_PREMIUM_EE");
			assertThat(line.evaluatedAmount()).isEqualByComparingTo("380.0000");
		});
	}

	@Test
	void aovMatchesGoldenScenarioFourPercentPremium() throws Exception {
		stubPlatformComponents();
		stubCompensation(false, true);
		PayrollRunState state = stateWithBases(Map.of("LOONBELASTING", new BigDecimal("8897.5003"), "GROSS",
				new BigDecimal("8897.5003"), "AOV", new BigDecimal("6462.5000")));

		contributor.contribute(state);

		assertThat(state.statutoryEvaluatedAmounts()).anySatisfy(line -> {
			assertThat(line.tenantWageComponentCode()).isEqualTo("SOCIAL_PREMIUM_EE");
			assertThat(line.evaluatedAmount()).isEqualByComparingTo("258.5000");
		});
	}

	@Test
	void contributesNothingWhenTaxSnapshotMissing() {
		var ctx = payrollContext();
		PayrollRunState state = new PayrollRunState(ctx);
		state.setEmployeeBaseTotals(Map.of(EMPLOYEE, Map.of("LOONBELASTING", new BigDecimal("10000.0000"))));

		contributor.contribute(state);

		assertThat(state.statutoryEvaluatedAmounts()).isEmpty();
	}

	private void stubPlatformComponents() {
		PlatformWageComponentEntity wageTax = platformComponent(WAGE_TAX_ID, "WAGE_TAX");
		PlatformWageComponentEntity aov = platformComponent(AOV_ID, "SOCIAL_PREMIUM_EE");
		when(platformWageComponentRepository.findByCountryCodeAndActiveIsTrueOrderByProcessingOrderAsc(anyString()))
				.thenReturn(List.of(wageTax, aov));
	}

	private void stubCompensation(boolean applyTaxes, boolean applyAov) {
		stubCompensation(applyTaxes, applyAov, true);
	}

	private void stubCompensation(boolean applyTaxes, boolean applyAov, boolean applyTaxExempt) {
		TenantEmployeeCompensationEntity compensation = new TenantEmployeeCompensationEntity();
		compensation.setEmployeeId(EMPLOYEE);
		compensation.setApplyTaxes(applyTaxes);
		compensation.setApplyAov(applyAov);
		compensation.setApplyTaxExempt(applyTaxExempt);
		when(compensationRepository.findByTenantIdAndEmployeeIdIn(TENANT, List.of(EMPLOYEE)))
				.thenReturn(List.of(compensation));
	}

	private PayrollRunState stateWithBases(BigDecimal baseAmount) throws Exception {
		return stateWithBases(Map.of("LOONBELASTING", baseAmount, "GROSS", baseAmount, "AOV", baseAmount));
	}

	private PayrollRunState stateWithBases(BigDecimal loonbelastingBase, BigDecimal grossBase) throws Exception {
		return stateWithBases(Map.of("LOONBELASTING", loonbelastingBase, "GROSS", grossBase, "AOV", loonbelastingBase));
	}

	private PayrollRunState stateWithBases(Map<String, BigDecimal> bases) throws Exception {
		return stateWithBases(bases, defaultSnapshot());
	}

	private PayrollRunState stateWithBases(Map<String, BigDecimal> bases, SurinameTaxRulesSnapshot snapshot) {
		PayrollRunState state = new PayrollRunState(payrollContext());
		state.setEmployeeBaseTotals(Map.of(EMPLOYEE, bases));
		state.countryRuleContext().putAttribute(SurinameCountryContextKeys.TAX_RULES_SNAPSHOT, snapshot);
		return state;
	}

	private SurinameTaxRulesSnapshot defaultSnapshot() throws Exception {
		ResolvedSurinameTaxRule wageTaxRule = wageTaxRule();
		ResolvedSurinameTaxRule aovRule = aovRule();
		ResolvedSurinameTaxRule taxFreeRule = taxFreeRule();
		return new SurinameTaxRulesSnapshot(LocalDate.of(2026, 2, 28),
				Map.of("SR_WAGE_TAX_DEFAULT", wageTaxRule, "SR_AOV_PREMIUM_MONTH", aovRule, "SR_TAX_FREE_WAGE_TAX_YEAR",
						taxFreeRule));
	}

	private SurinameTaxRulesSnapshot snapshotWithPensionCapRule() throws Exception {
		ResolvedSurinameTaxRule pensionCapRule = new ResolvedSurinameTaxRule(
				UUID.fromString("52000000-0000-0000-0000-000000000017"),
				SurinameCountryRuleKeys.RULE_AOV_BENEFICIARY_MONTH, "AOV beneficiary", LocalDate.of(2025, 3, 1), null,
				objectMapper.readTree("""
						{"v":2,"kind":"THRESHOLD_AMOUNT","freq":"MONTH","amount":2250}
						"""));
		Map<String, ResolvedSurinameTaxRule> rules = new java.util.HashMap<>(defaultSnapshot().rulesByCode());
		rules.put(SurinameCountryRuleKeys.RULE_AOV_BENEFICIARY_MONTH, pensionCapRule);
		return new SurinameTaxRulesSnapshot(LocalDate.of(2026, 2, 28), rules);
	}

	private void stubPeriodPayout(PayrollRunState state, String componentCode, BigDecimal amount) {
		UUID componentId = UUID.randomUUID();
		state.evaluatedComponentAmounts().add(EvaluatedComponentAmount.tenant(EMPLOYEE, componentId, componentCode,
				CalculationMethod.FIXED_AMOUNT.name(), amount, null));
		TenantWageComponentTransactionEntity tx = new TenantWageComponentTransactionEntity();
		tx.setId(UUID.randomUUID());
		tx.setTenantId(TENANT);
		tx.setCompanyId(COMPANY);
		tx.setEmployeeId(EMPLOYEE);
		tx.setPayPeriodId(PAY_PERIOD);
		tx.setTenantWageComponentId(componentId);
		tx.setAmount(amount);
		tx.setManualOverride(false);
		Instant now = Instant.now();
		tx.setCreatedAt(now);
		tx.setUpdatedAt(now);
		periodTransactions.add(tx);
	}

	private static BigDecimal wageTaxAmount(PayrollRunState state) {
		return state.statutoryEvaluatedAmounts().stream()
				.filter(line -> "WAGE_TAX".equals(line.tenantWageComponentCode()))
				.map(EvaluatedComponentAmount::evaluatedAmount)
				.findFirst()
				.orElse(BigDecimal.ZERO.setScale(4));
	}

	private static PayrollContext payrollContext() {
		return new PayrollContext(TENANT, COMPANY, "SR", "SRD", null, PAY_PERIOD, List.of(EMPLOYEE),
				LocalDate.of(2026, 2, 28));
	}

	private ResolvedSurinameTaxRule wageTaxRule() throws Exception {
		return new ResolvedSurinameTaxRule(UUID.fromString("52000000-0000-0000-0000-000000000001"), "SR_WAGE_TAX_DEFAULT",
				"Wage tax", LocalDate.of(2024, 1, 1), null, objectMapper.readTree("""
						{
						  "v": 2,
						  "freq": "YEAR",
						  "kind": "MARGINAL_RATES",
						  "rows": [
						    { "pct": 8,  "min": 0,      "max": 42000  },
						    { "pct": 18, "min": 42000,  "max": 84000  },
						    { "pct": 28, "min": 84000,  "max": 126000 },
						    { "pct": 38, "min": 126000 }
						  ]
						}
						"""));
	}

	private ResolvedSurinameTaxRule aovRule() throws Exception {
		return new ResolvedSurinameTaxRule(UUID.fromString("52000000-0000-0000-0000-000000000002"),
				"SR_AOV_PREMIUM_MONTH", "AOV", LocalDate.of(2024, 1, 1), null, objectMapper.readTree("""
						{"v":2,"freq":"MONTH","kind":"FLAT_RATE","pct":4}
						"""));
	}

	private ResolvedSurinameTaxRule taxFreeRule() throws Exception {
		return new ResolvedSurinameTaxRule(UUID.fromString("52000000-0000-0000-0000-000000000003"),
				"SR_TAX_FREE_WAGE_TAX_YEAR", "Tax free", LocalDate.of(2024, 1, 1), null,
				objectMapper.readTree(TAX_FREE_JSON));
	}

	private static final String TAX_FREE_JSON = """
			{"v":2,"kind":"THRESHOLD_AMOUNT","freq":"YEAR","amount":108000}
			""";

	private static PlatformWageComponentEntity platformComponent(UUID id, String code) {
		PlatformWageComponentEntity component = new PlatformWageComponentEntity();
		component.setId(id);
		component.setCode(code);
		component.setCalculationMethod(CalculationMethod.FIXED_AMOUNT);
		return component;
	}
}
