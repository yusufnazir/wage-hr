package com.wagepayroll.payroll;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.wagepayroll.domain.payrollbase.TenantWageComponentBaseEffectRepository;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentRepository;
import com.wagepayroll.payroll.base.WageComponentBaseEffectCopyService;
import com.wagepayroll.payroll.catalog.DefaultPayrollCatalogProvisioningService;
import com.wagepayroll.payroll.engine.DefaultPayrollEngine;
import com.wagepayroll.payroll.engine.EvaluatedComponentAmount;
import com.wagepayroll.payroll.engine.EvaluatedComponentSource;
import com.wagepayroll.payroll.engine.PayrollContext;
import com.wagepayroll.payroll.engine.PayrollRunResult;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SurinamePayrollGoldenIT {

	private static final UUID DEMO_TENANT = UUID.fromString("10000000-0000-0000-0000-000000000001");

	private static final UUID DEMO_COMPANY = UUID.fromString("5fa00000-0000-4000-8000-000000000001");

	private static final UUID ANDRE = UUID.fromString("5fa00000-0000-4000-8000-000000000006");

	private static final UUID FEB_2026_PERIOD = UUID.fromString("5fa00000-0000-4000-8000-00000000000c");

	private static final UUID BASIC_SALARY_COMPONENT = UUID.fromString("5fa00000-0000-4000-8000-00000000000f");

	private static final UUID TEMPLATE_1001 = UUID.fromString("51000000-0000-0000-0000-000000000001");

	@Autowired
	private DefaultPayrollEngine payrollEngine;

	@Autowired
	private WageComponentBaseEffectCopyService baseEffectCopyService;

	@Autowired
	private TenantWageComponentBaseEffectRepository tenantBaseEffectRepository;

	@Autowired
	private TenantWageComponentRepository tenantWageComponentRepository;

	@Autowired
	private DefaultPayrollCatalogProvisioningService catalogProvisioningService;

	@BeforeEach
	void ensureDemoCatalogAndBaseEffects() {
		catalogProvisioningService.provisionForCompany(DEMO_TENANT, DEMO_COMPANY, "SR");
		if (tenantBaseEffectRepository.findByTenantIdAndTenantWageComponentIdAndActiveIsTrue(DEMO_TENANT,
				BASIC_SALARY_COMPONENT).isEmpty()) {
			baseEffectCopyService.copyTemplateEffectsToTenantComponent(DEMO_TENANT, TEMPLATE_1001, BASIC_SALARY_COMPONENT);
		}
	}

	@Test
	void feb2026AndrePreviewMatchesGoldenScenario() {
		assertThat(tenantWageComponentRepository.findByIdAndTenantId(BASIC_SALARY_COMPONENT, DEMO_TENANT)).isPresent();

		PayrollContext ctx = new PayrollContext(DEMO_TENANT, DEMO_COMPANY, "SR", "SRD", null, FEB_2026_PERIOD,
				List.of(ANDRE), LocalDate.of(2026, 2, 28));
		PayrollRunResult result = payrollEngine.calculate(ctx);

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
		assertThat(bases.get("LOONBELASTING")).isEqualByComparingTo("8897.5003");

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
			assertThat(line.evaluatedAmount()).isEqualByComparingTo("270.5000");
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
				.hasSize(1).first().satisfies(line -> assertThat(line.evaluatedAmount()).isEqualByComparingTo("32.7500"));
		assertThat(result.evaluatedComponentAmounts()).filteredOn(line -> "1038".equals(line.tenantWageComponentCode()))
				.hasSize(1).first().satisfies(line -> assertThat(line.evaluatedAmount()).isEqualByComparingTo("32.7500"));
		assertThat(result.evaluatedComponentAmounts()).filteredOn(line -> "1004".equals(line.tenantWageComponentCode()))
				.hasSize(1).first().satisfies(line -> assertThat(line.evaluatedAmount()).isEqualByComparingTo("8897.5003"));

		assertThat(result.evaluatedComponentAmounts()).anySatisfy(line -> {
			assertThat(line.tenantWageComponentCode()).isEqualTo("1026");
			assertThat(line.evaluatedAmount()).isEqualByComparingTo("7251.9670");
		});

		assertThat(result.employeeNetPay().get(ANDRE)).isEqualByComparingTo("7251.9670");
	}
}
