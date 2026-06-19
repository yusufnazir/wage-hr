package com.wagepayroll.payroll.engine;

import java.util.List;

import org.springframework.stereotype.Component;

import com.wagepayroll.payroll.engine.phase.ContextPhaseHandler;
import com.wagepayroll.payroll.engine.phase.GrossAndBasesPhaseHandler;
import com.wagepayroll.payroll.engine.phase.NetAndAccumulatorsPhaseHandler;
import com.wagepayroll.payroll.engine.phase.PayrollPhaseHandler;
import com.wagepayroll.payroll.engine.phase.StatutoryPhaseHandler;

@Component
public class PayrollRunOrchestrator {

	private final List<PayrollPhaseHandler> handlers;

	public PayrollRunOrchestrator(ContextPhaseHandler contextPhaseHandler,
			GrossAndBasesPhaseHandler grossAndBasesPhaseHandler, StatutoryPhaseHandler statutoryPhaseHandler,
			NetAndAccumulatorsPhaseHandler netAndAccumulatorsPhaseHandler) {
		this.handlers = List.of(contextPhaseHandler, grossAndBasesPhaseHandler, statutoryPhaseHandler,
				netAndAccumulatorsPhaseHandler);
	}

	public PayrollRunResult run(PayrollContext context) {
		PayrollRunState state = new PayrollRunState(context);
		for (PayrollPhaseHandler handler : handlers) {
			handler.execute(state);
		}
		return state.toResult();
	}
}
