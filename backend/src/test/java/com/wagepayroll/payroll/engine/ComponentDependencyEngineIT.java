package com.wagepayroll.payroll.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.wagepayroll.domain.wagecomponent.PlatformWageComponentTemplateEntity;
import com.wagepayroll.domain.wagecomponent.PlatformWageComponentTemplateRepository;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentDependencyEntity;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentDependencyRepository;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentEntity;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentRepository;
import com.wagepayroll.payroll.model.CalculationMethod;
import com.wagepayroll.payroll.model.ComponentType;
import com.wagepayroll.payroll.model.NetEffect;
import com.wagepayroll.payroll.model.PayrollPhase;
import com.wagepayroll.payroll.model.RoundingStrategy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ComponentDependencyEngineIT {

	private static final UUID DEMO_TENANT = UUID.fromString("10000000-0000-0000-0000-000000000001");

	private static final UUID DEMO_COMPANY = UUID.fromString("5fa00000-0000-4000-8000-000000000001");

	private static final UUID FEB_2026_PERIOD = UUID.fromString("5fa00000-0000-4000-8000-00000000000c");

	private static final UUID ANDRE = UUID.fromString("5fa00000-0000-4000-8000-000000000006");

	@Autowired
	private DefaultPayrollEngine payrollEngine;

	@Autowired
	private TenantWageComponentRepository tenantWageComponentRepository;

	@Autowired
	private TenantWageComponentDependencyRepository tenantWageComponentDependencyRepository;

	@Autowired
	private PlatformWageComponentTemplateRepository platformWageComponentTemplateRepository;

	@Test
	void evaluatesDependentFormulaAfterPrerequisite() {
		TenantWageComponentEntity base = tenantWageComponentRepository
				.findByIdAndTenantId(UUID.fromString("5fa00000-0000-4000-8000-00000000000f"), DEMO_TENANT)
				.orElseThrow();
		PlatformWageComponentTemplateEntity bonusTemplate = platformWageComponentTemplateRepository
				.findByCountryCodeAndActiveIsTrueOrderByTemplateCodeAsc("SR").stream()
				.filter(t -> "1002".equals(t.getTemplateCode()))
				.findFirst()
				.orElseThrow();
		TenantWageComponentEntity bonus = new TenantWageComponentEntity();
		bonus.setId(UUID.randomUUID());
		bonus.setTenantId(DEMO_TENANT);
		bonus.setCompanyId(DEMO_COMPANY);
		bonus.setPlatformTemplateId(bonusTemplate.getId());
		bonus.setCode("1002");
		bonus.setName("Bonus 10% (test)");
		bonus.setComponentType(ComponentType.EARNING);
		bonus.setCategory("SALARY");
		bonus.setNetEffect(NetEffect.ADD_TO_NET);
		bonus.setCalculationMethod(CalculationMethod.FORMULA);
		bonus.setFormulaExpression("component(\"1001\").amount * 0.1");
		bonus.setDefaultAmount(BigDecimal.ZERO);
		bonus.setRoundingStrategy(RoundingStrategy.HALF_UP);
		bonus.setProcessingOrder(5);
		bonus.setPhase(PayrollPhase.GROSS);
		bonus.setActive(true);
		bonus.setCreatedAt(Instant.now());
		bonus.setUpdatedAt(Instant.now());
		tenantWageComponentRepository.save(bonus);

		TenantWageComponentDependencyEntity edge = new TenantWageComponentDependencyEntity();
		edge.setId(UUID.randomUUID());
		edge.setTenantId(DEMO_TENANT);
		edge.setTenantWageComponentId(bonus.getId());
		edge.setDependsOnTenantWageComponentId(base.getId());
		edge.setCreatedAt(Instant.now());
		edge.setUpdatedAt(Instant.now());
		tenantWageComponentDependencyRepository.save(edge);

		PayrollContext ctx = new PayrollContext(DEMO_TENANT, DEMO_COMPANY, "SR", "SRD", null, FEB_2026_PERIOD,
				List.of(ANDRE), LocalDate.of(2026, 2, 28));
		PayrollRunResult result = payrollEngine.calculate(ctx);

		assertThat(result.evaluatedComponentAmounts()).anySatisfy(line -> {
			assertThat(line.tenantWageComponentCode()).isEqualTo("1001");
			assertThat(line.evaluatedAmount()).isEqualByComparingTo("6000.0000");
		});
		assertThat(result.evaluatedComponentAmounts()).anySatisfy(line -> {
			assertThat(line.tenantWageComponentCode()).isEqualTo("1002");
			assertThat(line.evaluatedAmount()).isEqualByComparingTo("600.0000");
		});
	}
}
