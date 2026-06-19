package com.wagepayroll.payroll;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.wagepayroll.domain.payrollbase.TenantWageComponentBaseEffectRepository;
import com.wagepayroll.domain.wagecomponent.TenantPayrollLedgerPostingRepository;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentBalanceRepository;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentBalanceTransactionRepository;
import com.wagepayroll.payperiod.TenantPayPeriodService;
import com.wagepayroll.payroll.base.WageComponentBaseEffectCopyService;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PayrollLedgerPostingIT {

	private static final UUID DEMO_TENANT = UUID.fromString("10000000-0000-0000-0000-000000000001");
	private static final UUID ANDRE = UUID.fromString("5fa00000-0000-4000-8000-000000000006");
	private static final UUID FEB_2026_PERIOD = UUID.fromString("5fa00000-0000-4000-8000-00000000000c");
	private static final UUID BASIC_SALARY = UUID.fromString("5fa00000-0000-4000-8000-00000000000f");
	private static final UUID LOAN_BALANCE = UUID.fromString("5fa00000-0000-4000-8000-000000000024");
	private static final UUID TEMPLATE_1001 = UUID.fromString("51000000-0000-0000-0000-000000000001");
	private static final UUID ACTOR = UUID.fromString("30000000-0000-0000-0000-000000000001");

	@Autowired
	private TenantPayPeriodService payPeriodService;
	@Autowired
	private TenantPayrollFinalizeService finalizeService;
	@Autowired
	private TenantPayrollLedgerPostingRepository postingRepository;
	@Autowired
	private TenantWageComponentBalanceRepository balanceRepository;
	@Autowired
	private TenantWageComponentBalanceTransactionRepository balanceTxRepository;
	@Autowired
	private WageComponentBaseEffectCopyService baseEffectCopyService;
	@Autowired
	private TenantWageComponentBaseEffectRepository tenantBaseEffectRepository;
	@Autowired
	private com.wagepayroll.payroll.engine.DefaultPayrollEngine payrollEngine;

	@BeforeEach
	void ensureBaseEffects() {
		if (tenantBaseEffectRepository.findByTenantIdAndTenantWageComponentIdAndActiveIsTrue(DEMO_TENANT, BASIC_SALARY)
				.isEmpty()) {
			baseEffectCopyService.copyTemplateEffectsToTenantComponent(DEMO_TENANT, TEMPLATE_1001, BASIC_SALARY);
		}
	}

	@Test
	void finalizeCreatesPostingsAndReducesLoanBalance() {
		var run = payPeriodService.createRun(DEMO_TENANT,
				new TenantPayPeriodRunCreateRequest(FEB_2026_PERIOD, "FINAL"));
		var result = finalizeService.finalize(DEMO_TENANT, FEB_2026_PERIOD, run.id(),
				new TenantPayPeriodFinalizeRequest(List.of(ANDRE), false), ACTOR, "phase8-it");

		assertThat(result.postingsCreated()).isGreaterThanOrEqualTo(1);
		assertThat(result.balancesUpdated()).isGreaterThanOrEqualTo(1);
		assertThat(postingRepository.countByTenantIdAndPayPeriodRunId(DEMO_TENANT, run.id())).isGreaterThanOrEqualTo(1);

		var balance = balanceRepository.findById(LOAN_BALANCE).orElseThrow();
		assertThat(balance.getCurrentBalance()).isEqualByComparingTo("9500.0000");
		assertThat(balanceTxRepository.countByTenantIdAndPayPeriodRunId(DEMO_TENANT, run.id())).isEqualTo(1);
	}

	@Test
	void previewDoesNotCreatePostingsOrBalanceTx() {
		long postingsBefore = postingRepository.count();
		long balanceTxBefore = balanceTxRepository.count();
		var ctx = new com.wagepayroll.payroll.engine.PayrollContext(DEMO_TENANT,
				UUID.fromString("5fa00000-0000-4000-8000-000000000001"), "SR", "SRD", null, FEB_2026_PERIOD,
				List.of(ANDRE), java.time.LocalDate.of(2026, 2, 28));
		var engineResult = payrollEngine.calculate(ctx);
		assertThat(engineResult.evaluatedComponentAmounts()).isNotEmpty();
		assertThat(postingRepository.count()).isEqualTo(postingsBefore);
		assertThat(balanceTxRepository.count()).isEqualTo(balanceTxBefore);
		assertThat(engineResult.postingsCreated()).isZero();
		assertThat(engineResult.balancesUpdated()).isZero();
	}
}
