package com.wagepayroll.payroll;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.wagepayroll.domain.payrollbase.TenantWageComponentBaseEffectRepository;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentRepository;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentTransactionEntity;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentTransactionRepository;
import com.wagepayroll.payroll.base.WageComponentBaseEffectCopyService;
import com.wagepayroll.payroll.engine.DefaultPayrollEngine;
import com.wagepayroll.payroll.engine.PayrollContext;
import com.wagepayroll.payroll.engine.PayrollRunResult;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FormulaCriteriaRulesPayrollIT {

	private static final UUID DEMO_TENANT = UUID.fromString("10000000-0000-0000-0000-000000000001");

	private static final UUID DEMO_COMPANY = UUID.fromString("5fa00000-0000-4000-8000-000000000001");

	private static final UUID MARIA = UUID.fromString("5fa00000-0000-4000-8000-000000000005");

	private static final UUID ANDRE = UUID.fromString("5fa00000-0000-4000-8000-000000000006");

	private static final UUID FEB_2026_PERIOD = UUID.fromString("5fa00000-0000-4000-8000-00000000000c");

	private static final UUID BASIC_SALARY_COMPONENT = UUID.fromString("5fa00000-0000-4000-8000-00000000000f");

	private static final UUID TEMPLATE_1001 = UUID.fromString("51000000-0000-0000-0000-000000000001");

	private static final String CRITERIA_FORMULA = """
			{"formulaMode":"CRITERIA_RULES","formulaRules":[{"criteriaType":"WAGE_TYPE","itemKey":"PER_HOUR","formulaExpression":"transaction.quantity*transaction.rate"}],"defaultFormulaExpression":"compensation.periodic_rate"}
			""";

	@Autowired
	private DefaultPayrollEngine payrollEngine;

	@Autowired
	private WageComponentBaseEffectCopyService baseEffectCopyService;

	@Autowired
	private TenantWageComponentBaseEffectRepository tenantBaseEffectRepository;

	@Autowired
	private TenantWageComponentRepository tenantWageComponentRepository;

	@Autowired
	private TenantWageComponentTransactionRepository tenantWageComponentTransactionRepository;

	@BeforeEach
	void ensureDemoBaseEffects() {
		if (tenantBaseEffectRepository.findByTenantIdAndTenantWageComponentIdAndActiveIsTrue(DEMO_TENANT,
				BASIC_SALARY_COMPONENT).isEmpty()) {
			baseEffectCopyService.copyTemplateEffectsToTenantComponent(DEMO_TENANT, TEMPLATE_1001, BASIC_SALARY_COMPONENT);
		}
		var base = tenantWageComponentRepository.findByIdAndTenantId(BASIC_SALARY_COMPONENT, DEMO_TENANT).orElseThrow();
		base.setFormulaExpression(CRITERIA_FORMULA.trim());
		tenantWageComponentRepository.save(base);
	}

	@Test
	void wageTypeCriteriaSelectsDifferentFormulasForMariaAndAndre() {
		TenantWageComponentTransactionEntity mariaTx = new TenantWageComponentTransactionEntity();
		mariaTx.setId(UUID.randomUUID());
		mariaTx.setTenantId(DEMO_TENANT);
		mariaTx.setCompanyId(DEMO_COMPANY);
		mariaTx.setEmployeeId(MARIA);
		mariaTx.setPayPeriodId(FEB_2026_PERIOD);
		mariaTx.setTenantWageComponentId(BASIC_SALARY_COMPONENT);
		mariaTx.setQuantity(new BigDecimal("160"));
		mariaTx.setRate(new BigDecimal("100"));
		mariaTx.setAmount(new BigDecimal("16000"));
		mariaTx.setManualOverride(false);
		mariaTx.setCreatedAt(Instant.now());
		mariaTx.setUpdatedAt(Instant.now());
		tenantWageComponentTransactionRepository.save(mariaTx);

		PayrollContext ctx = new PayrollContext(DEMO_TENANT, DEMO_COMPANY, "SR", "SRD", null, FEB_2026_PERIOD,
				List.of(MARIA, ANDRE), LocalDate.of(2026, 2, 28));
		PayrollRunResult result = payrollEngine.calculate(ctx);

		assertThat(result.evaluatedComponentAmounts()).anySatisfy(line -> {
			assertThat(line.employeeId()).isEqualTo(MARIA);
			assertThat(line.tenantWageComponentCode()).isEqualTo("1001");
			assertThat(line.evaluatedAmount()).isEqualByComparingTo("16000.0000");
		});
		assertThat(result.evaluatedComponentAmounts()).anySatisfy(line -> {
			assertThat(line.employeeId()).isEqualTo(ANDRE);
			assertThat(line.tenantWageComponentCode()).isEqualTo("1001");
			assertThat(line.evaluatedAmount()).isEqualByComparingTo("6000.0000");
		});
	}
}
