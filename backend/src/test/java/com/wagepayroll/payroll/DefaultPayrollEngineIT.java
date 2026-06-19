package com.wagepayroll.payroll;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.wagepayroll.domain.wagecomponent.PlatformWageComponentRepository;
import com.wagepayroll.payroll.engine.DefaultPayrollEngine;
import com.wagepayroll.payroll.engine.PayrollContext;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DefaultPayrollEngineIT {

	@Autowired
	private DefaultPayrollEngine payrollEngine;

	@Autowired
	private PlatformWageComponentRepository platformWageComponentRepository;

	@Test
	void surinameStatutoryComponentsSeeded() {
		assertThat(platformWageComponentRepository.findByCountryCodeAndActiveIsTrueOrderByProcessingOrderAsc("SR")).hasSize(3);
	}

	@Test
	void calculateResolvesStatutoryRowCount() {
		var ctx = PayrollContext.withoutPinnedCountryRules(UUID.fromString("10000000-0000-0000-0000-000000000001"),
				UUID.fromString("f0000000-0000-0000-0000-000000000001"), "SR", "SRD",
				UUID.fromString("e0000000-0000-0000-0000-000000000001"), null, List.of());
		var result = payrollEngine.calculate(ctx);
		assertThat(result.resolvedStatutoryComponentCount()).isEqualTo(3);
		assertThat(result.evaluatedComponentAmounts()).isEmpty();
	}
}
