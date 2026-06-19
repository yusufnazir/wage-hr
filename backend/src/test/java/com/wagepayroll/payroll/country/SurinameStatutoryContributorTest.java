package com.wagepayroll.payroll.country;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
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
import com.wagepayroll.domain.org.TenantCompanyRepository;
import com.wagepayroll.domain.wagecomponent.PlatformWageComponentEntity;
import com.wagepayroll.domain.wagecomponent.PlatformWageComponentRepository;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentTransactionRepository;
import com.wagepayroll.payroll.engine.PayrollContext;
import com.wagepayroll.payroll.engine.PayrollRunState;
import com.wagepayroll.payroll.model.CalculationMethod;

@ExtendWith(MockitoExtension.class)
class SurinameStatutoryContributorTest {

	private static final UUID TENANT = UUID.randomUUID();
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

	private SurinameStatutoryContributor contributor;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@BeforeEach
	void setUp() {
		contributor = new SurinameStatutoryContributor(new SurinameWageTaxCalculator(),
				new SurinameCountryRuleAlgorithms(), platformWageComponentRepository, compensationRepository,
				companyRepository, transactionRepository);
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

	private void stubPlatformComponents() {
		PlatformWageComponentEntity wageTax = platformComponent(WAGE_TAX_ID, "WAGE_TAX");
		PlatformWageComponentEntity aov = platformComponent(AOV_ID, "SOCIAL_PREMIUM_EE");
		when(platformWageComponentRepository.findByCountryCodeAndActiveIsTrueOrderByProcessingOrderAsc(anyString()))
				.thenReturn(List.of(wageTax, aov));
	}

	private void stubCompensation(boolean applyTaxes, boolean applyAov) {
		TenantEmployeeCompensationEntity compensation = new TenantEmployeeCompensationEntity();
		compensation.setEmployeeId(EMPLOYEE);
		compensation.setApplyTaxes(applyTaxes);
		compensation.setApplyAov(applyAov);
		when(compensationRepository.findByTenantIdAndEmployeeIdIn(TENANT, List.of(EMPLOYEE)))
				.thenReturn(List.of(compensation));
	}

	private PayrollRunState stateWithBases(BigDecimal baseAmount) throws Exception {
		return stateWithBases(baseAmount, baseAmount);
	}

	private PayrollRunState stateWithBases(BigDecimal loonbelastingBase, BigDecimal grossBase) throws Exception {
		var ctx = PayrollContext.withoutPinnedCountryRules(TENANT, UUID.randomUUID(), "SR", "SRD", null, null,
				List.of(EMPLOYEE));
		PayrollRunState state = new PayrollRunState(ctx);
		state.setEmployeeBaseTotals(Map.of(EMPLOYEE,
				Map.of("LOONBELASTING", loonbelastingBase, "GROSS", grossBase, "AOV", loonbelastingBase)));

		ResolvedSurinameTaxRule wageTaxRule = new ResolvedSurinameTaxRule(
				UUID.fromString("52000000-0000-0000-0000-000000000001"), "SR_WAGE_TAX_DEFAULT", "Wage tax",
				LocalDate.of(2024, 1, 1), null, objectMapper.readTree("""
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
		ResolvedSurinameTaxRule aovRule = new ResolvedSurinameTaxRule(
				UUID.fromString("52000000-0000-0000-0000-000000000002"), "SR_AOV_PREMIUM_MONTH", "AOV",
				LocalDate.of(2024, 1, 1), null, objectMapper.readTree("""
						{"v":2,"freq":"MONTH","kind":"FLAT_RATE","pct":4}
						"""));
		ResolvedSurinameTaxRule taxFreeRule = new ResolvedSurinameTaxRule(
				UUID.fromString("52000000-0000-0000-0000-000000000003"), "SR_TAX_FREE_WAGE_TAX_YEAR", "Tax free",
				LocalDate.of(2024, 1, 1), null, objectMapper.readTree(TAX_FREE_JSON));
		SurinameTaxRulesSnapshot snapshot = new SurinameTaxRulesSnapshot(LocalDate.of(2026, 2, 28),
				Map.of("SR_WAGE_TAX_DEFAULT", wageTaxRule, "SR_AOV_PREMIUM_MONTH", aovRule, "SR_TAX_FREE_WAGE_TAX_YEAR",
						taxFreeRule));
		state.countryRuleContext().putAttribute(SurinameCountryContextKeys.TAX_RULES_SNAPSHOT, snapshot);
		return state;
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
