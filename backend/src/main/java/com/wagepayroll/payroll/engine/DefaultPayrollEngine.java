package com.wagepayroll.payroll.engine;

import org.springframework.stereotype.Service;

@Service
public class DefaultPayrollEngine implements PayrollEngine {

	private final PayrollRunOrchestrator payrollRunOrchestrator;

	public DefaultPayrollEngine(PayrollRunOrchestrator payrollRunOrchestrator) {
		this.payrollRunOrchestrator = payrollRunOrchestrator;
	}

	@Override
	public PayrollRunResult calculate(PayrollContext context) {
		return payrollRunOrchestrator.run(context);
	}
}
