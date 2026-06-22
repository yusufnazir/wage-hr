package com.wagepayroll.payroll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.byLessThan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.wagepayroll.domain.org.TenantEmployeeEntity;
import com.wagepayroll.domain.org.TenantEmployeeRepository;
import com.wagepayroll.domain.payrollbase.TenantWageComponentBaseEffectRepository;
import com.wagepayroll.domain.payrollstanding.TenantEmployeePayrollStandingInstructionRepository;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentRepository;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentTransactionRepository;
import com.wagepayroll.payroll.base.WageComponentBaseEffectCopyService;
import com.wagepayroll.payroll.catalog.DemoP2BenefitStandingSeeder;
import com.wagepayroll.payroll.catalog.DemoP4ExclusionStandingSeeder;
import com.wagepayroll.payroll.catalog.DemoSurinameArt10CatalogProvisioner;
import com.wagepayroll.payroll.catalog.DemoVariablePayStandingSeeder;
import com.wagepayroll.payroll.catalog.DefaultPayrollCatalogProvisioningService;
import com.wagepayroll.payroll.engine.DefaultPayrollEngine;
import com.wagepayroll.payroll.engine.EvaluatedComponentAmount;
import com.wagepayroll.payroll.engine.EvaluatedComponentSource;
import com.wagepayroll.payroll.engine.PayrollContext;
import com.wagepayroll.payroll.engine.PayrollRunResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SurinamePayrollGoldenIT {

	private static final UUID DEMO_TENANT = UUID.fromString("10000000-0000-0000-0000-000000000001");

	private static final UUID DEMO_COMPANY = UUID.fromString("5fa00000-0000-4000-8000-000000000001");

	private static final UUID ANDRE = UUID.fromString("5fa00000-0000-4000-8000-000000000006");

	private static final UUID FEB_2026_PERIOD = UUID.fromString("5fa00000-0000-4000-8000-00000000000c");

	private static final UUID BASIC_SALARY_COMPONENT = UUID.fromString("5fa00000-0000-4000-8000-00000000000f");

	private static final UUID TEMPLATE_1001 = UUID.fromString("51000000-0000-0000-0000-000000000001");

	private static final Set<String> P2_STANDING_TEMPLATE_CODES = Set.of("1049", "1050", "1051", "1052", "1053", "1054",
			"1057");

	private static final Set<String> P4_STANDING_TEMPLATE_CODES = Set.of("1058", "1060", "1062", "1064");

	private static final Set<String> ART10_STANDING_TEMPLATE_CODES;
	static {
		Set<String> codes = new java.util.HashSet<>(P2_STANDING_TEMPLATE_CODES);
		codes.addAll(P4_STANDING_TEMPLATE_CODES);
		ART10_STANDING_TEMPLATE_CODES = Set.copyOf(codes);
	}

	private static final BigDecimal BASELINE_LOONBELASTING = new BigDecimal("8897.5003");

	private static final BigDecimal P2_BENEFIT_LOONBELASTING_DELTA = new BigDecimal("1632.8125");

	@Autowired
	private DefaultPayrollEngine payrollEngine;

	@Autowired
	private WageComponentBaseEffectCopyService baseEffectCopyService;

	@Autowired
	private TenantWageComponentBaseEffectRepository tenantBaseEffectRepository;

	@Autowired
	private TenantWageComponentRepository tenantWageComponentRepository;

	@Autowired
	private TenantWageComponentTransactionRepository transactionRepository;

	@Autowired
	private DefaultPayrollCatalogProvisioningService catalogProvisioningService;

	@Autowired
	private TenantEmployeeRepository employeeRepository;

	@Autowired
	private TenantEmployeePayrollStandingInstructionRepository standingRepository;

	@Autowired
	private DemoVariablePayStandingSeeder variablePayStandingSeeder;

	@Autowired
	private DemoP2BenefitStandingSeeder p2BenefitStandingSeeder;

	@Autowired
	private DemoP4ExclusionStandingSeeder p4ExclusionStandingSeeder;

	@Autowired
	private DemoSurinameArt10CatalogProvisioner art10CatalogProvisioner;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@BeforeEach
	void ensureDemoCatalogAndBaseEffects() {
		commitDemoStandingSetup(false, false);
	}

	@Test
	@Order(1)
	void feb2026AndrePreviewMatchesGoldenScenario() {
		assertThat(tenantWageComponentRepository.findByIdAndTenantId(BASIC_SALARY_COMPONENT, DEMO_TENANT)).isPresent();

		PayrollRunResult result = calculateAndreFeb2026();

		assertThat(result.evaluatedComponentAmounts()).anySatisfy(line -> {
			assertThat(line.componentSource()).isEqualTo(EvaluatedComponentSource.TENANT);
			assertThat(line.tenantWageComponentCode()).isEqualTo("1001");
			assertThat(line.evaluatedAmount()).isEqualByComparingTo("6000.0000");
		});
		assertThat(result.evaluatedComponentAmounts()).anySatisfy(line -> {
			assertThat(line.tenantWageComponentCode()).isEqualTo("1006");
			assertThat(line.evaluatedAmount()).isEqualByComparingTo("500.0000");
		});
		assertThat(result.evaluatedComponentAmounts()).anySatisfy(line -> {
			assertThat(line.tenantWageComponentCode()).isEqualTo("1007");
			assertThat(line.evaluatedAmount()).isEqualByComparingTo("600.0000");
		});
		assertThat(result.evaluatedComponentAmounts()).anySatisfy(line -> {
			assertThat(line.tenantWageComponentCode()).isEqualTo("1045");
			assertThat(line.evaluatedAmount()).isEqualByComparingTo("259.6155");
		});

		Map<String, BigDecimal> bases = result.employeeBaseTotals().get(ANDRE);
		assertThat(bases).isNotNull();
		assertThat(bases.get("LOONBELASTING")).isEqualByComparingTo(BASELINE_LOONBELASTING);

		assertThat(result.evaluatedComponentAmounts()).filteredOn(line -> "1005".equals(line.tenantWageComponentCode()))
				.hasSize(1).first().satisfies(line -> assertThat(line.evaluatedAmount()).isEqualByComparingTo("8897.5003"));
		assertThat(result.evaluatedComponentAmounts()).filteredOn(line -> "1008".equals(line.tenantWageComponentCode()))
				.hasSize(1).first().satisfies(line -> assertThat(line.evaluatedAmount()).isEqualByComparingTo("250.0000"));
		assertThat(result.evaluatedComponentAmounts()).filteredOn(line -> "1023".equals(line.tenantWageComponentCode()))
				.hasSize(1).first().satisfies(line -> assertThat(line.evaluatedAmount()).isEqualByComparingTo("250.0000"));
		assertThat(result.evaluatedComponentAmounts().stream()
				.filter(line -> "WAGE_TAX".equals(line.tenantWageComponentCode())).findAny()).isEmpty();

		assertThat(result.evaluatedComponentAmounts()).anySatisfy(line -> {
			assertThat(line.componentSource()).isEqualTo(EvaluatedComponentSource.PLATFORM);
			assertThat(line.tenantWageComponentCode()).isEqualTo("SOCIAL_PREMIUM_EE");
			assertThat(line.evaluatedAmount()).isEqualByComparingTo("258.5000");
		});

		assertThat(result.evaluatedComponentAmounts()).anySatisfy(line -> {
			assertThat(line.tenantWageComponentCode()).isEqualTo("1018");
			assertThat(line.evaluatedAmount()).isEqualByComparingTo("14.4000");
		});

		assertThat(result.evaluatedComponentAmounts()).anySatisfy(line -> {
			assertThat(line.tenantWageComponentCode()).isEqualTo("1025");
			assertThat(line.evaluatedAmount()).isEqualByComparingTo("64.8000");
		});

		assertThat(result.evaluatedComponentAmounts()).anySatisfy(line -> {
			assertThat(line.tenantWageComponentCode()).isEqualTo("1013");
			assertThat(line.evaluatedAmount()).isEqualByComparingTo("27.0000");
		});

		assertThat(result.evaluatedComponentAmounts()).anySatisfy(line -> {
			assertThat(line.tenantWageComponentCode()).isEqualTo("1020");
			assertThat(line.evaluatedAmount()).isEqualByComparingTo("33.7500");
		});

		assertThat(result.evaluatedComponentAmounts()).anySatisfy(line -> {
			assertThat(line.tenantWageComponentCode()).isEqualTo("1014");
			assertThat(line.evaluatedAmount()).isEqualByComparingTo("20.0000");
		});

		assertThat(result.evaluatedComponentAmounts()).anySatisfy(line -> {
			assertThat(line.tenantWageComponentCode()).isEqualTo("1015");
			assertThat(line.evaluatedAmount()).isEqualByComparingTo("24.0000");
		});

		assertThat(result.evaluatedComponentAmounts()).anySatisfy(line -> {
			assertThat(line.componentSource()).isEqualTo(EvaluatedComponentSource.TENANT);
			assertThat(line.tenantWageComponentCode()).isEqualTo("1003");
			assertThat(line.evaluatedAmount()).isEqualByComparingTo("500.0000");
		});

		assertThat(result.evaluatedComponentAmounts()).anySatisfy(line -> {
			assertThat(line.componentSource()).isEqualTo(EvaluatedComponentSource.TENANT);
			assertThat(line.tenantWageComponentCode()).isEqualTo("1042");
			assertThat(line.evaluatedAmount()).isEqualByComparingTo("16.6667");
		});

		assertThat(result.evaluatedComponentAmounts()).anySatisfy(line -> {
			assertThat(line.componentSource()).isEqualTo(EvaluatedComponentSource.TENANT);
			assertThat(line.tenantWageComponentCode()).isEqualTo("1021");
			assertThat(line.evaluatedAmount()).isEqualByComparingTo("0.0000");
		});

		assertThat(result.evaluatedComponentAmounts()).anySatisfy(line -> {
			assertThat(line.tenantWageComponentCode()).isEqualTo("1043");
			assertThat(line.evaluatedAmount()).isEqualByComparingTo("212.5000");
		});
		assertThat(result.evaluatedComponentAmounts()).anySatisfy(line -> {
			assertThat(line.tenantWageComponentCode()).isEqualTo("1044");
			assertThat(line.evaluatedAmount()).isEqualByComparingTo("212.5000");
		});

		assertThat(result.evaluatedComponentAmounts()).filteredOn(line -> "1037".equals(line.tenantWageComponentCode()))
				.hasSize(1).first().satisfies(line -> assertThat(line.evaluatedAmount()).isEqualByComparingTo("31.2500"));
		assertThat(result.evaluatedComponentAmounts()).filteredOn(line -> "1038".equals(line.tenantWageComponentCode()))
				.hasSize(1).first().satisfies(line -> assertThat(line.evaluatedAmount()).isEqualByComparingTo("31.2500"));
		assertThat(result.evaluatedComponentAmounts()).filteredOn(line -> "1004".equals(line.tenantWageComponentCode()))
				.hasSize(1).first().satisfies(line -> assertThat(line.evaluatedAmount()).isEqualByComparingTo("8897.5003"));

		assertThat(result.evaluatedComponentAmounts()).anySatisfy(line -> {
			assertThat(line.tenantWageComponentCode()).isEqualTo("1026");
			assertThat(line.evaluatedAmount()).isEqualByComparingTo("7250.4670");
		});

		assertThat(result.employeeNetPay().get(ANDRE)).isEqualByComparingTo("7250.4670");
	}

	@Test
	@Order(2)
	void feb2026AndrePreviewIncludesP2DerivedLines() {
		commitDemoStandingSetup(true, false);

		PayrollRunResult result = calculateAndreFeb2026();
		List<EvaluatedComponentAmount> lines = result.evaluatedComponentAmounts();

		assertLineAmount(lines, "1049", "300.0000");
		assertLineAmount(lines, "1050", "667.3125");
		assertLineAmount(lines, "1051", "150.0000");
		assertLineAmount(lines, "1052", "100.0000");
		assertLineAmount(lines, "1053", "110.0000");
		assertLineAmount(lines, "1054", "30.0000");
		assertLineAmount(lines, "1057", "275.5000");

		assertThat(result.employeeBaseTotals().get(ANDRE).get("LOONBELASTING"))
				.isCloseTo(BASELINE_LOONBELASTING.add(P2_BENEFIT_LOONBELASTING_DELTA), byLessThan(new BigDecimal("0.0010")));
	}

	@Test
	@Order(3)
	void feb2026AndrePreviewIncludesP4ExclusionPairs() {
		commitDemoStandingSetup(false, true);

		PayrollRunResult result = calculateAndreFeb2026();
		List<EvaluatedComponentAmount> lines = result.evaluatedComponentAmounts();

		assertLineAmount(lines, "1058", "425.0000");
		assertLineAmount(lines, "1059", "425.0000");
		assertLineAmount(lines, "1060", "1200.0000");
		assertLineAmount(lines, "1061", "1200.0000");
		assertLineAmount(lines, "1062", "3500.0000");
		assertLineAmount(lines, "1063", "3500.0000");
		assertLineAmount(lines, "1064", "3000.0000");
		assertLineAmount(lines, "1065", "3000.0000");
	}

	private PayrollRunResult calculateAndreFeb2026() {
		PayrollContext ctx = new PayrollContext(DEMO_TENANT, DEMO_COMPANY, "SR", "SRD", null, FEB_2026_PERIOD,
				List.of(ANDRE), LocalDate.of(2026, 2, 28));
		return payrollEngine.calculate(ctx);
	}

	private void commitDemoStandingSetup(boolean seedP2Benefits, boolean seedP4Exclusions) {
		TransactionTemplate tx = new TransactionTemplate(transactionManager);
		tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		tx.executeWithoutResult(status -> {
			catalogProvisioningService.provisionForCompany(DEMO_TENANT, DEMO_COMPANY, "SR");
			if (tenantBaseEffectRepository.findByTenantIdAndTenantWageComponentIdAndActiveIsTrue(DEMO_TENANT,
					BASIC_SALARY_COMPONENT).isEmpty()) {
				baseEffectCopyService.copyTemplateEffectsToTenantComponent(DEMO_TENANT, TEMPLATE_1001,
						BASIC_SALARY_COMPONENT);
			}
			standingRepository.findByTenantIdAndCompanyIdAndEmployeeIdOrderByEffectiveFromAsc(DEMO_TENANT, DEMO_COMPANY,
					ANDRE).stream()
					.filter(row -> BASIC_SALARY_COMPONENT.equals(row.getTenantWageComponentId()))
					.forEach(row -> {
						row.setAmount(null);
						row.setAmountOverride(false);
						standingRepository.save(row);
					});
			resetArt10Standing(seedP2Benefits, seedP4Exclusions);
			if (seedP2Benefits || seedP4Exclusions) {
				art10CatalogProvisioner.provisionForCompany(DEMO_TENANT, DEMO_COMPANY, "SR");
			}
			List<TenantEmployeeEntity> employees = employeeRepository
					.findByTenantIdAndCompanyIdOrderByBadgeNumberAsc(DEMO_TENANT, DEMO_COMPANY);
			variablePayStandingSeeder.seedDemoCompany(DEMO_TENANT, DEMO_COMPANY, employees);
			if (seedP2Benefits) {
				p2BenefitStandingSeeder.seedAndreP2Benefits(DEMO_TENANT, DEMO_COMPANY, employees);
			}
			if (seedP4Exclusions) {
				p4ExclusionStandingSeeder.seedAndreP4Exclusions(DEMO_TENANT, DEMO_COMPANY, employees);
			}
			transactionRepository
					.findByTenantIdAndPayPeriodIdAndEmployeeIdAndTenantWageComponentId(DEMO_TENANT, FEB_2026_PERIOD,
							ANDRE, BASIC_SALARY_COMPONENT)
					.ifPresent(transactionRepository::delete);
		});
	}

	private void resetArt10Standing(boolean keepP2Standing, boolean keepP4Standing) {
		Set<String> codesToClear = new java.util.HashSet<>(ART10_STANDING_TEMPLATE_CODES);
		if (keepP2Standing) {
			codesToClear.removeAll(P2_STANDING_TEMPLATE_CODES);
		}
		if (keepP4Standing) {
			codesToClear.removeAll(P4_STANDING_TEMPLATE_CODES);
		}
		if (codesToClear.isEmpty()) {
			return;
		}
		Map<UUID, String> componentCodeById = tenantWageComponentRepository
				.findByTenantIdAndCompanyIdAndActiveIsTrueOrderByProcessingOrderAsc(DEMO_TENANT, DEMO_COMPANY)
				.stream()
				.filter(c -> codesToClear.contains(c.getCode()))
				.collect(java.util.stream.Collectors.toMap(c -> c.getId(), c -> c.getCode(), (a, b) -> a));
		if (componentCodeById.isEmpty()) {
			return;
		}
		standingRepository.findByTenantIdAndCompanyIdAndEmployeeIdOrderByEffectiveFromAsc(DEMO_TENANT, DEMO_COMPANY,
				ANDRE).stream()
				.filter(row -> componentCodeById.containsKey(row.getTenantWageComponentId()))
				.forEach(row -> {
					row.setActive(false);
					standingRepository.save(row);
				});
		transactionRepository
				.findByTenantIdAndCompanyIdAndPayPeriodIdAndEmployeeIdIn(DEMO_TENANT, DEMO_COMPANY, FEB_2026_PERIOD,
						List.of(ANDRE))
				.stream()
				.filter(tx -> componentCodeById.containsKey(tx.getTenantWageComponentId()))
				.forEach(transactionRepository::delete);
	}

	private static void assertLineAmount(List<EvaluatedComponentAmount> lines, String code, String amount) {
		assertThat(lines).filteredOn(line -> code.equals(line.tenantWageComponentCode())).hasSize(1).first()
				.satisfies(line -> assertThat(line.evaluatedAmount()).isEqualByComparingTo(amount));
	}
}
