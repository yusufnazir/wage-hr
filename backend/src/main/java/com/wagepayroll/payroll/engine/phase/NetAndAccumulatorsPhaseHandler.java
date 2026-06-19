package com.wagepayroll.payroll.engine.phase;

import org.springframework.stereotype.Component;

import com.wagepayroll.payroll.NetPayCalculator;
import com.wagepayroll.payroll.NetWageLineSynchronizer;
import com.wagepayroll.payroll.PayrollFinalizePostProcessor;
import com.wagepayroll.payroll.PayrollResultPersistenceService;
import com.wagepayroll.payroll.PayrollYtdAccumulatorService;
import com.wagepayroll.payroll.engine.PayrollRunPhase;
import com.wagepayroll.payroll.engine.PayrollRunState;

@Component
public class NetAndAccumulatorsPhaseHandler implements PayrollPhaseHandler {

	private final NetPayCalculator netPayCalculator;

	private final NetWageLineSynchronizer netWageLineSynchronizer;
	private final PayrollYtdAccumulatorService payrollYtdAccumulatorService;
	private final PayrollResultPersistenceService payrollResultPersistenceService;
	private final PayrollFinalizePostProcessor payrollFinalizePostProcessor;

	public NetAndAccumulatorsPhaseHandler(NetPayCalculator netPayCalculator,
			NetWageLineSynchronizer netWageLineSynchronizer,
			PayrollYtdAccumulatorService payrollYtdAccumulatorService,
			PayrollResultPersistenceService payrollResultPersistenceService,
			PayrollFinalizePostProcessor payrollFinalizePostProcessor) {
		this.netPayCalculator = netPayCalculator;
		this.netWageLineSynchronizer = netWageLineSynchronizer;
		this.payrollYtdAccumulatorService = payrollYtdAccumulatorService;
		this.payrollResultPersistenceService = payrollResultPersistenceService;
		this.payrollFinalizePostProcessor = payrollFinalizePostProcessor;
	}

	@Override
	public PayrollRunPhase phase() {
		return PayrollRunPhase.NET_AND_ACCUMULATORS;
	}

	@Override
	public void execute(PayrollRunState state) {
		var netByEmployee = netPayCalculator.compute(state);
		state.setEmployeeNetPay(netByEmployee);
		for (var entry : netByEmployee.entrySet()) {
			state.calculationTrace().addSummary("NET", entry.getKey(), "Net pay (after all components)",
					"Sum of earnings minus deductions from evaluated tenant and statutory lines.", entry.getValue());
		}
		netWageLineSynchronizer.sync(state, netByEmployee);
		if (state.context().payPeriodRunId() != null) {
			payrollYtdAccumulatorService.applyPeriodDeltas(state);
			payrollResultPersistenceService.persistRun(state);
			payrollFinalizePostProcessor.afterPersist(state);
		}
	}
}
