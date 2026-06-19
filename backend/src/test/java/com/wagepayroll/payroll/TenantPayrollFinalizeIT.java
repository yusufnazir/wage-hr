package com.wagepayroll.payroll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.wagepayroll.api.dto.TenantPayPeriodFinalizeRequest;
import com.wagepayroll.api.dto.TenantPayPeriodFinalizeResultDto;
import com.wagepayroll.api.dto.TenantPayPeriodRunCreateRequest;
import com.wagepayroll.api.dto.TenantPayrollResultLineRowDto;
import com.wagepayroll.domain.payrollbase.TenantWageComponentBaseEffectRepository;
import com.wagepayroll.domain.wagecomponent.TenantPayrollLedgerPostingRepository;
import com.wagepayroll.domain.wagecomponent.TenantPayrollResultLineRepository;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentBalanceTransactionRepository;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentRepository;
import com.wagepayroll.payperiod.TenantPayPeriodService;
import com.wagepayroll.payroll.base.WageComponentBaseEffectCopyService;
import com.wagepayroll.payroll.engine.DefaultPayrollEngine;
import com.wagepayroll.payroll.engine.PayrollContext;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TenantPayrollFinalizeIT {

	private static final UUID DEMO_TENANT = UUID.fromString("10000000-0000-0000-0000-000000000001");

	private static final UUID DEMO_COMPANY = UUID.fromString("5fa00000-0000-4000-8000-000000000001");

	private static final UUID ANDRE = UUID.fromString("5fa00000-0000-4000-8000-000000000006");

	private static final UUID FEB_2026_PERIOD = UUID.fromString("5fa00000-0000-4000-8000-00000000000c");

	private static final UUID BASIC_SALARY_COMPONENT = UUID.fromString("5fa00000-0000-4000-8000-00000000000f");

	private static final UUID TEMPLATE_1001 = UUID.fromString("51000000-0000-0000-0000-000000000001");

	private static final UUID ACTOR = UUID.fromString("30000000-0000-0000-0000-000000000001");

	@Autowired
	private TenantPayPeriodService payPeriodService;

	@Autowired
	private TenantPayrollFinalizeService finalizeService;

	@Autowired
	private DefaultPayrollEngine payrollEngine;

	@Autowired
	private TenantPayrollResultLineRepository resultLineRepository;

	@Autowired
	private WageComponentBaseEffectCopyService baseEffectCopyService;

	@Autowired
	private TenantWageComponentBaseEffectRepository tenantBaseEffectRepository;

	@Autowired
	private TenantWageComponentRepository tenantWageComponentRepository;

	@Autowired
	private TenantPayrollLedgerPostingRepository postingRepository;

	@Autowired
	private TenantWageComponentBalanceTransactionRepository balanceTxRepository;

	@BeforeEach
	void ensureDemoBaseEffects() {
		if (tenantBaseEffectRepository.findByTenantIdAndTenantWageComponentIdAndActiveIsTrue(DEMO_TENANT,
				BASIC_SALARY_COMPONENT).isEmpty()) {
			baseEffectCopyService.copyTemplateEffectsToTenantComponent(DEMO_TENANT, TEMPLATE_1001, BASIC_SALARY_COMPONENT);
		}
	}

	@Test
	void finalizePersistsGoldenLinesForAndre() {
		assertThat(tenantWageComponentRepository.findByIdAndTenantId(BASIC_SALARY_COMPONENT, DEMO_TENANT)).isPresent();

		var run = payPeriodService.createRun(DEMO_TENANT,
				new TenantPayPeriodRunCreateRequest(FEB_2026_PERIOD, "FINAL"));
		TenantPayPeriodFinalizeResultDto result = finalizeService.finalize(DEMO_TENANT, FEB_2026_PERIOD, run.id(),
				new TenantPayPeriodFinalizeRequest(List.of(ANDRE), false), ACTOR, "test-finalize");

		assertThat(result.linesCreated()).isGreaterThanOrEqualTo(5);
		assertThat(result.employeeCount()).isEqualTo(1);

		List<TenantPayrollResultLineRowDto> lines = finalizeService.listResultLines(DEMO_TENANT, run.id(), ANDRE);
		assertThat(lines).anySatisfy(line -> {
			assertThat(line.componentRefId()).isEqualTo(BASIC_SALARY_COMPONENT);
			assertThat(line.componentSource()).isEqualTo("TENANT");
			assertThat(line.roundedAmount()).isEqualByComparingTo("6000.0000");
		});
		assertThat(lines).anySatisfy(line -> assertThat(line.roundedAmount()).isEqualByComparingTo("768.2500"));
		assertThat(lines).anySatisfy(line -> assertThat(line.roundedAmount()).isEqualByComparingTo("248.5000"));
		assertThat(lines).anySatisfy(line -> assertThat(line.roundedAmount()).isEqualByComparingTo("212.5000"));
		assertThat(lines).anySatisfy(line -> assertThat(line.roundedAmount()).isEqualByComparingTo("27.0000"));
		assertThat(lines).anySatisfy(line -> assertThat(line.roundedAmount()).isEqualByComparingTo("33.7500"));
		assertThat(lines).anySatisfy(line -> assertThat(line.roundedAmount()).isEqualByComparingTo("20.0000"));
		assertThat(lines).anySatisfy(line -> assertThat(line.roundedAmount()).isEqualByComparingTo("24.0000"));
		assertThat(lines).anySatisfy(line -> assertThat(line.roundedAmount()).isEqualByComparingTo("500.0000"));
		assertThat(lines).anySatisfy(line -> assertThat(line.roundedAmount()).isEqualByComparingTo("16.6667"));
		assertThat(result.employeeNetPay().get(ANDRE)).isEqualByComparingTo("5927.6670");
		assertThat(result.postingsCreated()).isGreaterThanOrEqualTo(1);
		assertThat(postingRepository.countByTenantIdAndPayPeriodRunId(DEMO_TENANT, run.id())).isGreaterThanOrEqualTo(1);
		assertThat(balanceTxRepository.countByTenantIdAndPayPeriodRunId(DEMO_TENANT, run.id())).isGreaterThanOrEqualTo(1);
	}

	@Test
	void duplicateFinalizeRejected() {
		var run = payPeriodService.createRun(DEMO_TENANT,
				new TenantPayPeriodRunCreateRequest(FEB_2026_PERIOD, "FINAL"));
		var request = new TenantPayPeriodFinalizeRequest(List.of(ANDRE), false);
		finalizeService.finalize(DEMO_TENANT, FEB_2026_PERIOD, run.id(), request, ACTOR, "test-1");
		assertThatThrownBy(
				() -> finalizeService.finalize(DEMO_TENANT, FEB_2026_PERIOD, run.id(), request, ACTOR, "test-2"))
				.isInstanceOf(ResponseStatusException.class).satisfies(ex -> {
					var rse = (ResponseStatusException) ex;
					assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
					assertThat(rse.getReason()).isEqualTo("RUN_ALREADY_FINALIZED");
				});
	}

	@Test
	void previewWithoutRunIdDoesNotPersistLines() {
		long before = resultLineRepository.count();
		var ctx = new PayrollContext(DEMO_TENANT, DEMO_COMPANY, "SR", "SRD", null, FEB_2026_PERIOD, List.of(ANDRE),
				java.time.LocalDate.of(2026, 2, 28));
		var engineResult = payrollEngine.calculate(ctx);
		assertThat(engineResult.evaluatedComponentAmounts()).isNotEmpty();
		assertThat(resultLineRepository.count()).isEqualTo(before);
		assertThat(engineResult.persistedResultLineCount()).isZero();
	}
}
