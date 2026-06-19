package com.wagepayroll.payroll;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.wagepayroll.api.dto.TenantPayPeriodFinalizeRequest;
import com.wagepayroll.api.dto.TenantPayPeriodRunCreateRequest;
import com.wagepayroll.domain.payroll.TenantPayrollYtdAccumulatorRepository;
import com.wagepayroll.domain.payrollbase.TenantWageComponentBaseEffectRepository;
import com.wagepayroll.payperiod.TenantPayPeriodService;
import com.wagepayroll.payroll.base.WageComponentBaseEffectCopyService;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PayrollYtdAccumulatorIT {

	private static final UUID DEMO_TENANT = UUID.fromString("10000000-0000-0000-0000-000000000001");

	private static final UUID ANDRE = UUID.fromString("5fa00000-0000-4000-8000-000000000006");

	private static final UUID JAN_2026_PERIOD = UUID.fromString("5fa00000-0000-4000-8000-00000000000b");

	private static final UUID FEB_2026_PERIOD = UUID.fromString("5fa00000-0000-4000-8000-00000000000c");

	private static final UUID BASIC_SALARY_COMPONENT = UUID.fromString("5fa00000-0000-4000-8000-00000000000f");

	private static final UUID TEMPLATE_1001 = UUID.fromString("51000000-0000-0000-0000-000000000001");

	private static final UUID ACTOR = UUID.fromString("30000000-0000-0000-0000-000000000001");

	@Autowired
	private TenantPayPeriodService payPeriodService;

	@Autowired
	private TenantPayrollFinalizeService finalizeService;

	@Autowired
	private TenantPayrollYtdAccumulatorRepository ytdRepository;

	@Autowired
	private WageComponentBaseEffectCopyService baseEffectCopyService;

	@Autowired
	private TenantWageComponentBaseEffectRepository tenantBaseEffectRepository;

	@BeforeEach
	void ensureDemoBaseEffects() {
		if (tenantBaseEffectRepository.findByTenantIdAndTenantWageComponentIdAndActiveIsTrue(DEMO_TENANT,
				BASIC_SALARY_COMPONENT).isEmpty()) {
			baseEffectCopyService.copyTemplateEffectsToTenantComponent(DEMO_TENANT, TEMPLATE_1001, BASIC_SALARY_COMPONENT);
		}
	}

	@Test
	void loonbelastingYtdIncrementsAcrossTwoPeriods() {
		var janRun = payPeriodService.createRun(DEMO_TENANT,
				new TenantPayPeriodRunCreateRequest(JAN_2026_PERIOD, "FINAL"));
		finalizeService.finalize(DEMO_TENANT, JAN_2026_PERIOD, janRun.id(),
				new TenantPayPeriodFinalizeRequest(List.of(ANDRE), false), ACTOR, "ytd-jan");

		var febRun = payPeriodService.createRun(DEMO_TENANT,
				new TenantPayPeriodRunCreateRequest(FEB_2026_PERIOD, "FINAL"));
		finalizeService.finalize(DEMO_TENANT, FEB_2026_PERIOD, febRun.id(),
				new TenantPayPeriodFinalizeRequest(List.of(ANDRE), false), ACTOR, "ytd-feb");

		var row = ytdRepository.findByTenantIdAndEmployeeIdAndTaxYearAndAccumulatorCode(DEMO_TENANT, ANDRE, 2026,
				"LOONBELASTING");
		assertThat(row).isPresent();
		assertThat(row.get().getAmount()).isEqualByComparingTo(new BigDecimal("37000.0000"));
	}
}
