package com.wagepayroll.payroll.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.wagepayroll.payroll.engine.phase.ContextPhaseHandler;
import com.wagepayroll.payroll.engine.phase.GrossAndBasesPhaseHandler;
import com.wagepayroll.payroll.engine.phase.NetAndAccumulatorsPhaseHandler;
import com.wagepayroll.payroll.engine.phase.StatutoryPhaseHandler;

@ExtendWith(MockitoExtension.class)
class PayrollRunOrchestratorTest {

	@Mock
	private ContextPhaseHandler contextHandler;

	@Mock
	private GrossAndBasesPhaseHandler grossHandler;

	@Mock
	private StatutoryPhaseHandler statutoryHandler;

	@Mock
	private NetAndAccumulatorsPhaseHandler netHandler;

	private PayrollRunOrchestrator orchestrator;

	@BeforeEach
	void setUp() {
		orchestrator = new PayrollRunOrchestrator(contextHandler, grossHandler, statutoryHandler, netHandler);
	}

	@Test
	void runExecutesPhasesInOrder() {
		var ctx = PayrollContext.withoutPinnedCountryRules(UUID.randomUUID(), UUID.randomUUID(), "SR", "SRD", null, null,
				List.of());
		orchestrator.run(ctx);
		InOrder order = inOrder(contextHandler, grossHandler, statutoryHandler, netHandler);
		order.verify(contextHandler).execute(org.mockito.ArgumentMatchers.any(PayrollRunState.class));
		order.verify(grossHandler).execute(org.mockito.ArgumentMatchers.any(PayrollRunState.class));
		order.verify(statutoryHandler).execute(org.mockito.ArgumentMatchers.any(PayrollRunState.class));
		order.verify(netHandler).execute(org.mockito.ArgumentMatchers.any(PayrollRunState.class));
		verifyNoMoreInteractions(contextHandler, grossHandler, statutoryHandler, netHandler);
	}

	@Test
	void runReturnsEmptyResultWhenNoEmployees() {
		var ctx = PayrollContext.withoutPinnedCountryRules(UUID.randomUUID(), UUID.randomUUID(), "SR", "SRD", null, null,
				List.of());
		PayrollRunResult result = orchestrator.run(ctx);
		assertThat(result.evaluatedComponentAmounts()).isEmpty();
		assertThat(result.employeeBaseTotals()).isEmpty();
	}
}
